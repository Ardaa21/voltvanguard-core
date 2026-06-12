/// Full-screen live telemetry view for a single EV.
///
/// Data sources:
///   • Static info  — [vehicleDetailProvider] (REST, cached)
///   • Live metrics — [vehicleTelemetryProvider(id)] (WebSocket stream)
///
/// Sections:
///   1. Hero battery gauge with animated arc
///   2. Live metrics strip (speed, range, temperature)
///   3. Battery history sparkline (last 60 readings, in-memory)
///   4. Location chip (lat/lng or "No GPS")
///   5. Active tasks badge linking to ReservationsScreen
library;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_theme.dart';
import '../../../dashboard/data/models/vehicle_telemetry_model.dart';
import '../../../dashboard/presentation/providers/telemetry_provider.dart';
import '../../../dashboard/presentation/widgets/battery_indicator.dart';
import '../../../vehicle/data/models/vehicle_model.dart';

// ── Battery history (in-memory ring buffer) ───────────────────────────────────

class _BatteryHistoryNotifier extends StateNotifier<List<double>> {
  _BatteryHistoryNotifier() : super([]);

  static const int _maxPoints = 60;

  void add(double pct) {
    if (state.length >= _maxPoints) {
      state = [...state.sublist(1), pct];
    } else {
      state = [...state, pct];
    }
  }
}

final _batteryHistoryProvider = StateNotifierProvider.family<
    _BatteryHistoryNotifier, List<double>, String>(
  (ref, _) => _BatteryHistoryNotifier(),
);

// ── Screen ────────────────────────────────────────────────────────────────────

class VehicleDetailScreen extends ConsumerWidget {
  const VehicleDetailScreen({super.key, required this.vehicleId});
  final String vehicleId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final vehicleAsync   = ref.watch(vehicleDetailProvider(vehicleId));
    final telemetryAsync = ref.watch(vehicleTelemetryProvider(vehicleId));

    // Feed incoming telemetry into history buffer
    ref.listen(vehicleTelemetryProvider(vehicleId), (_, next) {
      if (next.hasValue && next.value!.batteryPercent != null) {
        ref.read(_batteryHistoryProvider(vehicleId).notifier)
            .add(next.value!.batteryPercent!);
      }
    });

    final history = ref.watch(_batteryHistoryProvider(vehicleId));

    return vehicleAsync.when(
      loading: () => const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => Scaffold(
        appBar: AppBar(title: const Text('Vehicle')),
        body: Center(child: Text('Error: $e')),
      ),
      data: (vehicle) => _DetailView(
        vehicle:   vehicle,
        telemetry: telemetryAsync.valueOrNull,
        history:   history,
      ),
    );
  }
}

class _DetailView extends StatelessWidget {
  const _DetailView({
    required this.vehicle,
    required this.telemetry,
    required this.history,
  });

  final VehicleModel            vehicle;
  final VehicleTelemetryModel?  telemetry;
  final List<double>            history;

  @override
  Widget build(BuildContext context) {
    final batteryPct = telemetry?.batteryPercent ?? vehicle.batteryPercent ?? 0;
    final status     = telemetry?.status != null
        ? VehicleStatus.fromString(telemetry!.status)
        : vehicle.status;

    return Scaffold(
      backgroundColor: AppTheme.deepNavy,
      appBar: AppBar(
        leading: IconButton(
          icon:      const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () => context.pop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(vehicle.displayName,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            Text(vehicle.vin,
                style: const TextStyle(fontSize: 11, color: AppTheme.mutedBlue, letterSpacing: 1)),
          ],
        ),
        actions: [
          TextButton.icon(
            // IMPORTANT: use context.go, NOT context.push.
            // /reservations is a StatefulShellRoute branch — it's already
            // registered in the navigator. push() would add a second entry
            // for the same route key → "!keyReservation.contains(key)" crash.
            onPressed: () => context.go('/reservations'),
            icon:  const Icon(Icons.bolt, size: 16, color: AppTheme.electricTeal),
            label: const Text('Tasks', style: TextStyle(color: AppTheme.electricTeal)),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Status bar ──────────────────────────────────────────────────
          _StatusChip(status: status),
          const SizedBox(height: 24),

          // ── Hero battery gauge ──────────────────────────────────────────
          Center(
            child: BatteryGauge(
              percent:     batteryPct,
              size:        160,
              strokeWidth: 14,
            ),
          ),
          const SizedBox(height: 24),

          // ── Live metrics ────────────────────────────────────────────────
          _MetricsRow(vehicle: vehicle, telemetry: telemetry),
          const SizedBox(height: 24),

          // ── Battery history chart ───────────────────────────────────────
          if (history.length >= 2) ...[
            const _SectionTitle('Battery History'),
            const SizedBox(height: 12),
            _BatterySparkline(history: history),
            const SizedBox(height: 24),
          ],

          // ── Location ────────────────────────────────────────────────────
          const _SectionTitle('Location'),
          const SizedBox(height: 8),
          _LocationTile(vehicle: vehicle, telemetry: telemetry),
          const SizedBox(height: 24),

          // ── Agent info ──────────────────────────────────────────────────
          const _SectionTitle('AI Agent'),
          const SizedBox(height: 8),
          _AgentInfoCard(vehicle: vehicle),
        ],
      ),
    );
  }
}

// ── Sub-widgets ────────────────────────────────────────────────────────────────

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});
  final VehicleStatus status;

  @override
  Widget build(BuildContext context) {
    final (color, label) = switch (status) {
      VehicleStatus.online          => (AppTheme.statusOnline,  'ONLINE'),
      VehicleStatus.charging        => (AppTheme.statusCharging,'CHARGING'),
      VehicleStatus.batteryCritical => (AppTheme.crimson,       'CRITICAL'),
      VehicleStatus.offline         => (AppTheme.statusOffline, 'OFFLINE'),
      _                             => (AppTheme.mutedBlue,     'UNKNOWN'),
    };
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color:        color.withOpacity(0.12),
            borderRadius: BorderRadius.circular(20),
            border:       Border.all(color: color.withOpacity(0.4)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 6, height: 6,
                decoration: BoxDecoration(color: color, shape: BoxShape.circle),
              ),
              const SizedBox(width: 6),
              Text(label,
                style: TextStyle(
                  fontFamily:   'Inter',
                  fontSize:     11,
                  fontWeight:   FontWeight.w700,
                  color:        color,
                  letterSpacing: 1.5,
                )),
            ],
          ),
        ),
      ],
    );
  }
}

class _MetricsRow extends StatelessWidget {
  const _MetricsRow({required this.vehicle, required this.telemetry});
  final VehicleModel           vehicle;
  final VehicleTelemetryModel? telemetry;

  @override
  Widget build(BuildContext context) {
    final rangeKm  = telemetry?.estimatedRangeKm ?? vehicle.estimatedRangeKm;
    final speedKmh = telemetry?.speedKmh;
    final tempC    = telemetry?.batteryTemperatureCelsius;

    return Row(
      children: [
        _MetricCard(
          icon:  Icons.route_outlined,
          value: rangeKm != null ? '${rangeKm.toStringAsFixed(0)} km' : '—',
          label: 'Range',
        ),
        const SizedBox(width: 10),
        _MetricCard(
          icon:  Icons.speed_outlined,
          value: speedKmh != null ? '${speedKmh.toStringAsFixed(0)} km/h' : '—',
          label: 'Speed',
        ),
        const SizedBox(width: 10),
        _MetricCard(
          icon:  Icons.thermostat_outlined,
          value: tempC != null ? '${tempC.toStringAsFixed(1)} °C' : '—',
          label: 'Bat Temp',
        ),
      ],
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.icon, required this.value, required this.label});
  final IconData icon;
  final String   value;
  final String   label;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
      decoration: BoxDecoration(
        color:        AppTheme.cardNavy,
        borderRadius: BorderRadius.circular(12),
        border:       const Border.fromBorderSide(
          BorderSide(color: Color(0xFF1E3A5A)),
        ),
      ),
      child: Column(
        children: [
          Icon(icon, color: AppTheme.electricTeal, size: 20),
          const SizedBox(height: 6),
          Text(value,
            style: const TextStyle(
              fontFamily: 'Inter', fontSize: 15, fontWeight: FontWeight.w700, color: AppTheme.softWhite,
            )),
          Text(label,
            style: const TextStyle(
              fontFamily: 'Inter', fontSize: 10, color: AppTheme.mutedBlue,
            )),
        ],
      ),
    ),
  );
}

class _BatterySparkline extends StatelessWidget {
  const _BatterySparkline({required this.history});
  final List<double> history;

  @override
  Widget build(BuildContext context) {
    final spots = history.asMap().entries
        .map((e) => FlSpot(e.key.toDouble(), e.value))
        .toList();

    return Container(
      height: 100,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color:        AppTheme.cardNavy,
        borderRadius: BorderRadius.circular(12),
        border:       const Border.fromBorderSide(BorderSide(color: Color(0xFF1E3A5A))),
      ),
      child: LineChart(
        LineChartData(
          minY: 0, maxY: 100,
          gridData:    const FlGridData(show: false),
          borderData:  FlBorderData(show: false),
          titlesData:  const FlTitlesData(show: false),
          lineBarsData: [
            LineChartBarData(
              spots:         spots,
              isCurved:      true,
              color:         AppTheme.electricTeal,
              barWidth:      2,
              dotData:       const FlDotData(show: false),
              belowBarData:  BarAreaData(
                show:  true,
                color: AppTheme.electricTeal.withOpacity(0.08),
              ),
            ),
          ],
        ),
      ),
    ).animate().fadeIn(duration: 400.ms);
  }
}

class _LocationTile extends StatelessWidget {
  const _LocationTile({required this.vehicle, required this.telemetry});
  final VehicleModel           vehicle;
  final VehicleTelemetryModel? telemetry;

  @override
  Widget build(BuildContext context) {
    final lat = telemetry?.latitude ?? vehicle.latitude;
    final lng = telemetry?.longitude ?? vehicle.longitude;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color:        AppTheme.cardNavy,
        borderRadius: BorderRadius.circular(12),
        border:       const Border.fromBorderSide(BorderSide(color: Color(0xFF1E3A5A))),
      ),
      child: Row(
        children: [
          const Icon(Icons.location_on_outlined, color: AppTheme.electricTeal, size: 20),
          const SizedBox(width: 10),
          lat != null && lng != null
              ? Text(
                  '${lat.toStringAsFixed(5)}, ${lng.toStringAsFixed(5)}',
                  style: const TextStyle(fontFamily: 'Inter', fontSize: 13, color: AppTheme.softWhite),
                )
              : const Text('No GPS data', style: TextStyle(color: AppTheme.mutedBlue)),
        ],
      ),
    );
  }
}

class _AgentInfoCard extends StatelessWidget {
  const _AgentInfoCard({required this.vehicle});
  final VehicleModel vehicle;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      gradient: LinearGradient(
        colors: [AppTheme.electricTeal.withOpacity(0.08), AppTheme.cardNavy],
        begin:  Alignment.topLeft,
        end:    Alignment.bottomRight,
      ),
      borderRadius: BorderRadius.circular(12),
      border:       Border.all(color: AppTheme.electricTeal.withOpacity(0.25)),
    ),
    child: Row(
      children: [
        const Icon(Icons.smart_toy_outlined, color: AppTheme.electricTeal, size: 24),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Route Optimizer Agent',
                style: TextStyle(fontFamily: 'Inter', fontWeight: FontWeight.w600, fontSize: 13, color: AppTheme.softWhite),
              ),
              const SizedBox(height: 2),
              Text(
                vehicle.isBatteryCritical
                    ? 'Evaluating critical charging need…'
                    : 'Monitoring battery and range',
                style: const TextStyle(fontFamily: 'Inter', fontSize: 11, color: AppTheme.mutedBlue),
              ),
            ],
          ),
        ),
        Container(
          width: 8, height: 8,
          decoration: const BoxDecoration(color: AppTheme.electricTeal, shape: BoxShape.circle),
        )
            .animate(onPlay: (c) => c.repeat(reverse: true))
            .scaleXY(end: 1.5, duration: 900.ms),
      ],
    ),
  );
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(
    text.toUpperCase(),
    style: const TextStyle(
      fontFamily:   'Inter',
      fontSize:     10,
      fontWeight:   FontWeight.w700,
      color:        AppTheme.mutedBlue,
      letterSpacing: 1.5,
    ),
  );
}

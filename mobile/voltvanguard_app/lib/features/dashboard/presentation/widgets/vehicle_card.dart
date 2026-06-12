/// Vehicle summary card shown in the fleet grid.
///
/// Displays static vehicle info from [VehicleModel] overlaid with live
/// telemetry from [latestTelemetryProvider] when the WebSocket delivers it.
///
/// Uses [StatefulConsumerWidget] (not StatelessConsumerWidget) so we can
/// hold a tap-guard flag that prevents double-push navigation errors.
library;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_theme.dart';
import '../../../vehicle/data/models/vehicle_model.dart';
import '../providers/telemetry_provider.dart';
import '../../../dashboard/data/models/vehicle_telemetry_model.dart';
import 'battery_indicator.dart';

class VehicleCard extends ConsumerStatefulWidget {
  const VehicleCard({super.key, required this.vehicle});

  final VehicleModel vehicle;

  @override
  ConsumerState<VehicleCard> createState() => _VehicleCardState();
}

class _VehicleCardState extends ConsumerState<VehicleCard> {
  // Guard prevents duplicate pushes when user taps quickly more than once.
  bool _navigating = false;

  void _openDetail(BuildContext context) {
    if (_navigating) return;
    _navigating = true;
    context.push('/vehicle/${widget.vehicle.id}').whenComplete(() {
      if (mounted) setState(() => _navigating = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    // Overlay live telemetry if available; fall back to REST snapshot.
    final telemetryAsync = ref.watch(latestTelemetryProvider(widget.vehicle.id));
    final telemetry      = telemetryAsync.valueOrNull;

    // ── Battery ─────────────────────────────────────────────────────────────
    // Prefer live WS value, then REST snapshot, then null (show placeholder).
    // NEVER default to 0 — 0% and "no data" are semantically different.
    final double? batteryPct =
        telemetry?.batteryPercent ?? widget.vehicle.batteryPercent;

    final status     = telemetry?.status != null
        ? VehicleStatus.fromString(telemetry!.status)
        : widget.vehicle.status;
    final rangKm     = telemetry?.estimatedRangeKm ?? widget.vehicle.estimatedRangeKm;
    final speedKmh   = telemetry?.speedKmh;

    // Only flag critical if we have an actual reading ≤ 15%.
    final isCritical = batteryPct != null && batteryPct <= 15;

    return GestureDetector(
      onTap: () => _openDetail(context),
      child: AnimatedContainer(
        duration:   300.ms,
        curve:      Curves.easeOut,
        decoration: BoxDecoration(
          color:        isCritical
              ? AppTheme.crimson.withOpacity(0.12)
              : AppTheme.cardNavy,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isCritical
                ? AppTheme.crimson.withOpacity(0.5)
                : const Color(0xFF1E3A5A),
            width: isCritical ? 1.5 : 1,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Header: status dot + VIN ──────────────────────────────
              Row(
                children: [
                  _StatusDot(status: status),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      widget.vehicle.vin,
                      style: const TextStyle(
                        fontFamily:    'Inter',
                        fontSize:      11,
                        fontWeight:    FontWeight.w500,
                        color:         AppTheme.mutedBlue,
                        letterSpacing: 1.2,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (telemetry?.isSimulated == true)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color:        AppTheme.electricTeal.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        'SIM',
                        style: TextStyle(
                          fontFamily:    'Inter',
                          fontSize:      9,
                          fontWeight:    FontWeight.w700,
                          color:         AppTheme.electricTeal,
                          letterSpacing: 0.8,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 12),

              // ── Battery gauge ─────────────────────────────────────────
              // Show placeholder when no reading yet (avoids misleading "0%").
              Center(
                child: batteryPct != null
                    ? BatteryGauge(percent: batteryPct, size: 72, animate: true)
                    : const _NoBatteryGauge(),
              ),
              const SizedBox(height: 12),

              // ── Battery strip ─────────────────────────────────────────
              batteryPct != null
                  ? BatteryStrip(percent: batteryPct)
                  : const _NoBatteryStrip(),
              const SizedBox(height: 12),

              // ── Vehicle name ──────────────────────────────────────────
              Text(
                '${widget.vehicle.manufacturer} ${widget.vehicle.model}',
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize:   13,
                  fontWeight: FontWeight.w600,
                  color:      AppTheme.softWhite,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 4),

              // ── Stats row ─────────────────────────────────────────────
              Row(
                children: [
                  if (rangKm != null) ...[
                    _StatChip(
                      icon:  Icons.route_outlined,
                      label: '${rangKm.toStringAsFixed(0)} km',
                    ),
                    const SizedBox(width: 6),
                  ],
                  if (speedKmh != null && speedKmh > 0)
                    _StatChip(
                      icon:  Icons.speed_outlined,
                      label: '${speedKmh.toStringAsFixed(0)} km/h',
                    ),
                ],
              ),

              // ── Live pulse indicator ──────────────────────────────────
              if (telemetry != null) ...[
                const Spacer(),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Container(
                      width: 6, height: 6,
                      decoration: const BoxDecoration(
                        color: AppTheme.electricTeal,
                        shape: BoxShape.circle,
                      ),
                    )
                        .animate(onPlay: (c) => c.repeat())
                        .scaleXY(end: 2.0, duration: 1200.ms)
                        .fadeOut(begin: 1, duration: 1200.ms),
                    const SizedBox(width: 4),
                    const Text(
                      'LIVE',
                      style: TextStyle(
                        fontFamily:    'Inter',
                        fontSize:      9,
                        fontWeight:    FontWeight.w700,
                        color:         AppTheme.electricTeal,
                        letterSpacing: 0.8,
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    ).animate().fadeIn(duration: 400.ms).slideY(begin: 0.05, end: 0);
  }
}

// ── No-data battery placeholders ──────────────────────────────────────────────

class _NoBatteryGauge extends StatelessWidget {
  const _NoBatteryGauge();

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 72, height: 72,
    child: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.battery_unknown, color: AppTheme.mutedBlue, size: 28),
          const SizedBox(height: 2),
          const Text(
            '— %',
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize:   13,
              fontWeight: FontWeight.w700,
              color:      AppTheme.mutedBlue,
            ),
          ),
          const Text(
            'BAT',
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize:   9,
              color:      AppTheme.mutedBlue,
              letterSpacing: 1,
            ),
          ),
        ],
      ),
    ),
  );
}

class _NoBatteryStrip extends StatelessWidget {
  const _NoBatteryStrip();

  @override
  Widget build(BuildContext context) => Container(
    height: 6,
    decoration: BoxDecoration(
      color:        AppTheme.cardNavy,
      borderRadius: BorderRadius.circular(4),
      border:       Border.all(color: AppTheme.mutedBlue.withOpacity(0.2)),
    ),
    child: const Center(
      child: Text(
        'No telemetry',
        style: TextStyle(
          fontFamily: 'Inter', fontSize: 8, color: AppTheme.mutedBlue,
        ),
      ),
    ),
  );
}

// ── Sub-widgets ───────────────────────────────────────────────────────────────

class _StatusDot extends StatelessWidget {
  const _StatusDot({required this.status});
  final VehicleStatus status;

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      VehicleStatus.online          => AppTheme.statusOnline,
      VehicleStatus.charging        => AppTheme.statusCharging,
      VehicleStatus.batteryCritical => AppTheme.statusCritical,
      VehicleStatus.offline         => AppTheme.statusOffline,
      _                             => AppTheme.mutedBlue,
    };
    return Container(
      width: 8, height: 8,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    );
  }
}

class _StatChip extends StatelessWidget {
  const _StatChip({required this.icon, required this.label});
  final IconData icon;
  final String   label;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Icon(icon, size: 11, color: AppTheme.mutedBlue),
      const SizedBox(width: 3),
      Text(
        label,
        style: const TextStyle(
          fontFamily: 'Inter',
          fontSize:   11,
          color:      AppTheme.mutedBlue,
        ),
      ),
    ],
  );
}

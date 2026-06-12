/// Horizontal stats row pinned below the app bar showing live fleet KPIs.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shimmer/shimmer.dart';

import '../../../../core/theme/app_theme.dart';
import '../../../vehicle/data/models/vehicle_model.dart';
import '../providers/telemetry_provider.dart';

class FleetStatsBar extends ConsumerWidget {
  const FleetStatsBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summaryAsync = ref.watch(fleetSummaryProvider);

    return summaryAsync.when(
      loading: () => _LoadingBar(),
      error:   (_, __) => const _ErrorBar(),
      data:    (s) => _StatsRow(summary: s),
    );
  }
}

class _StatsRow extends StatelessWidget {
  const _StatsRow({required this.summary});
  final FleetSummaryModel summary;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding:    const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: const BoxDecoration(
        color: AppTheme.surfaceNavy,
        border: Border(bottom: BorderSide(color: Color(0xFF1E3A5A))),
      ),
      child: Row(
        children: [
          _Kpi(
            value: '${summary.totalVehicles}',
            label: 'Total',
            color: AppTheme.softWhite,
          ),
          _divider(),
          _Kpi(
            value: '${summary.onlineVehicles}',
            label: 'Online',
            color: AppTheme.statusOnline,
          ),
          _divider(),
          _Kpi(
            value: '${summary.chargingVehicles}',
            label: 'Charging',
            color: AppTheme.statusCharging,
          ),
          _divider(),
          _Kpi(
            value: '${summary.criticalVehicles}',
            label: 'Critical',
            color: AppTheme.crimson,
          ),
          _divider(),
          _Kpi(
            value: '${summary.averageBatteryPercent.toStringAsFixed(0)}%',
            label: 'Avg Bat',
            color: AppTheme.batteryColor(summary.averageBatteryPercent),
          ),
        ],
      ),
    );
  }

  Widget _divider() => Container(
    width: 1, height: 28,
    margin: const EdgeInsets.symmetric(horizontal: 12),
    color: const Color(0xFF1E3A5A),
  );
}

class _Kpi extends StatelessWidget {
  const _Kpi({required this.value, required this.label, required this.color});
  final String value;
  final String label;
  final Color  color;

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(
        value,
        style: TextStyle(
          fontFamily: 'Inter',
          fontSize:   18,
          fontWeight: FontWeight.w700,
          color:      color,
          height:     1.1,
        ),
      ),
      Text(
        label,
        style: const TextStyle(
          fontFamily: 'Inter',
          fontSize:   10,
          color:      AppTheme.mutedBlue,
          letterSpacing: 0.5,
        ),
      ),
    ],
  );
}

class _LoadingBar extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Shimmer.fromColors(
    baseColor:      AppTheme.cardNavy,
    highlightColor: AppTheme.surfaceNavy,
    child: Container(
      height: 56,
      color:  AppTheme.cardNavy,
    ),
  );
}

class _ErrorBar extends StatelessWidget {
  const _ErrorBar();

  @override
  Widget build(BuildContext context) => Container(
    height:  40,
    color:   AppTheme.surfaceNavy,
    alignment: Alignment.center,
    child: const Text(
      'Fleet stats unavailable',
      style: TextStyle(fontSize: 12, color: AppTheme.mutedBlue),
    ),
  );
}

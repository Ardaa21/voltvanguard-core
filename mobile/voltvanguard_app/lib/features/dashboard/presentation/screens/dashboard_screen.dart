/// Fleet overview dashboard — the app's home screen.
///
/// Layout (top → bottom):
///   AppBar  — title + WS connection indicator + refresh
///   FleetStatsBar — KPI row (total / online / charging / critical / avg bat)
///   Vehicle grid  — 2-column, each card backed by live WebSocket telemetry
///   Pull-to-refresh supported on the whole scroll view.
library;

import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shimmer/shimmer.dart';

import '../../../../core/network/websocket_service.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../vehicle/data/models/vehicle_model.dart';
import '../../data/models/vehicle_telemetry_model.dart';
import '../providers/telemetry_provider.dart';
import '../widgets/fleet_stats_bar.dart';
import '../widgets/vehicle_card.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Subscribe to notification watcher — keeps it alive while screen is mounted
    ref.watch(
      // Import via task provider, but activate here
      vehicleRepositoryProvider, // side-effect: WebSocket is connected
    );

    final vehiclesAsync = ref.watch(vehicleListProvider);
    final wsState       = ref.watch(wsConnectionStateProvider);

    return Scaffold(
      backgroundColor: AppTheme.deepNavy,
      appBar: _buildAppBar(context, ref, wsState),
      body: Column(
        children: [
          const FleetStatsBar(),
          Expanded(
            child: RefreshIndicator(
              color:       AppTheme.electricTeal,
              onRefresh:   () async {
                ref.invalidate(vehicleListProvider);
                ref.invalidate(fleetSummaryProvider);
              },
              child: vehiclesAsync.when(
                loading: () => _LoadingGrid(),
                error:   (e, _) => _ErrorView(error: e.toString(), onRetry: () => ref.invalidate(vehicleListProvider)),
                data:    (vehicles) => _VehicleGrid(vehicles: vehicles),
              ),
            ),
          ),
        ],
      ),
    );
  }

  PreferredSizeWidget _buildAppBar(
    BuildContext context,
    WidgetRef ref,
    AsyncValue<WsConnectionState> wsState,
  ) {
    return AppBar(
      backgroundColor: AppTheme.deepNavy,
      title: Row(
        children: [
          Container(
            width: 28, height: 28,
            decoration: BoxDecoration(
              color:        AppTheme.electricTeal.withOpacity(0.15),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.bolt, color: AppTheme.electricTeal, size: 18),
          ),
          const SizedBox(width: 10),
          const Text('VoltVanguard'),
        ],
      ),
      actions: [
        // ── WebSocket connection dot ──────────────────────────────────────
        Padding(
          padding: const EdgeInsets.only(right: 4),
          child: wsState.when(
            loading: () => const SizedBox(),
            error:   (_, __) => const Icon(Icons.wifi_off, color: AppTheme.crimson, size: 18),
            data:    (state) => _WsDot(state: state),
          ),
        ),
        // ── Refresh ───────────────────────────────────────────────────────
        IconButton(
          icon:    const Icon(Icons.refresh_rounded),
          tooltip: 'Refresh fleet',
          onPressed: () {
            ref.invalidate(vehicleListProvider);
            ref.invalidate(fleetSummaryProvider);
          },
        ),
      ],
    );
  }
}

// ── Vehicle grid ───────────────────────────────────────────────────────────────

class _VehicleGrid extends StatelessWidget {
  const _VehicleGrid({required this.vehicles});
  final List<VehicleModel> vehicles;

  @override
  Widget build(BuildContext context) {
    if (vehicles.isEmpty) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.electric_car_outlined, size: 64, color: AppTheme.mutedBlue),
            SizedBox(height: 16),
            Text('No vehicles registered', style: TextStyle(color: AppTheme.mutedBlue)),
          ],
        ),
      );
    }

    return GridView.builder(
      padding:          const EdgeInsets.all(16),
      gridDelegate:     const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount:    2,
        mainAxisSpacing:   12,
        crossAxisSpacing:  12,
        childAspectRatio:  0.78,
      ),
      itemCount: vehicles.length,
      itemBuilder: (context, index) => VehicleCard(
        // ValueKey ensures Flutter tracks cards by vehicle ID, not list index.
        // Without this, adding/removing vehicles causes cards to swap state.
        key:     ValueKey(vehicles[index].id),
        vehicle: vehicles[index],
      ),
    );
  }
}

// ── Loading skeleton ───────────────────────────────────────────────────────────

class _LoadingGrid extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding:      const EdgeInsets.all(16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount:   2,
        mainAxisSpacing:  12,
        crossAxisSpacing: 12,
        childAspectRatio: 0.78,
      ),
      itemCount: 6,
      itemBuilder: (_, __) => Shimmer.fromColors(
        baseColor:      AppTheme.cardNavy,
        highlightColor: AppTheme.surfaceNavy,
        child: Container(
          decoration: BoxDecoration(
            color:        AppTheme.cardNavy,
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
    );
  }
}

// ── Error state ────────────────────────────────────────────────────────────────

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error, required this.onRetry});
  final String error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_rounded, size: 56, color: AppTheme.mutedBlue),
            const SizedBox(height: 16),
            Text(
              'Could not load vehicles',
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              error,
              style: const TextStyle(fontSize: 12, color: AppTheme.mutedBlue),
              textAlign: TextAlign.center,
              maxLines:  3,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: onRetry,
              icon:  const Icon(Icons.refresh),
              label: const Text('Retry'),
            ),
          ],
        ),
      ),
    ).animate().fadeIn(duration: 300.ms);
  }
}

// ── WebSocket status dot ───────────────────────────────────────────────────────

class _WsDot extends StatelessWidget {
  const _WsDot({required this.state});
  final WsConnectionState state;

  @override
  Widget build(BuildContext context) {
    final (color, tooltip) = switch (state) {
      WsConnectionState.connected    => (AppTheme.statusOnline,   'Live telemetry active'),
      WsConnectionState.connecting   => (AppTheme.amber,          'Connecting…'),
      WsConnectionState.reconnecting => (AppTheme.amber,          'Reconnecting…'),
      WsConnectionState.disconnected => (AppTheme.statusOffline,  'Disconnected'),
    };

    Widget dot = Container(
      width: 10, height: 10,
      margin: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    );

    if (state == WsConnectionState.connected) {
      dot = dot
          .animate(onPlay: (c) => c.repeat())
          .scaleXY(end: 1.6, duration: 1000.ms)
          .fadeOut(begin: 1, duration: 1000.ms);
    }

    return Tooltip(message: tooltip, child: dot);
  }
}

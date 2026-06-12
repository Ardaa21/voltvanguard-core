/// Riverpod providers for the dashboard feature.
///
/// Provider tree:
///   vehicleRepositoryProvider          — singleton, owns ApiClient + WsService
///   ├── fleetSummaryProvider           — FutureProvider<FleetSummaryModel>
///   ├── vehicleListProvider            — FutureProvider<List<VehicleModel>>
///   ├── criticalVehiclesProvider       — FutureProvider<List<VehicleModel>>
///   ├── vehicleDetailProvider(id)      — FutureProvider.family<VehicleModel>
///   ├── telemetryStreamProvider        — StreamProvider<VehicleTelemetryModel>
///   ├── vehicleTelemetryProvider(id)   — StreamProvider.family (per-vehicle)
///   ├── latestTelemetryProvider(id)    — StreamProvider.family (alias, used by cards)
///   └── wsConnectionStateProvider      — StreamProvider<WsConnectionState>
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_client.dart';
import '../../../../core/network/websocket_service.dart';
import '../../../vehicle/data/models/vehicle_model.dart';
import '../../data/models/vehicle_telemetry_model.dart';
import '../../data/repositories/vehicle_repository_impl.dart';
import '../../domain/repositories/vehicle_repository.dart';

// ── Infrastructure providers ──────────────────────────────────────────────────

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(),
  name: 'apiClientProvider',
);

final wsServiceProvider = Provider<WebSocketService>(
  (ref) {
    final service = WebSocketService();
    ref.onDispose(service.dispose);
    return service;
  },
  name: 'wsServiceProvider',
);

final vehicleRepositoryProvider = Provider<VehicleRepository>(
  (ref) => VehicleRepositoryImpl(
    apiClient: ref.watch(apiClientProvider),
    wsService: ref.watch(wsServiceProvider),
  ),
  name: 'vehicleRepositoryProvider',
);

// ── Fleet data providers ───────────────────────────────────────────────────────

final fleetSummaryProvider = FutureProvider<FleetSummaryModel>(
  (ref) async {
    final repo = ref.watch(vehicleRepositoryProvider);

    // Fire both requests concurrently — saves one network round-trip.
    // vehicleListProvider is reused to avoid a duplicate HTTP call when
    // both fleetSummaryProvider and vehicleListProvider are watched together.
    final summaryFuture  = repo.getFleetSummary();
    final vehiclesFuture = ref.watch(vehicleListProvider.future);

    final summary  = await summaryFuture;
    final vehicles = await vehiclesFuture;

    // The fleet-stats endpoint only returns status counts, not avg battery.
    // Compute it client-side from the REST vehicle list snapshot.
    final withBat = vehicles
        .where((v) => v.batteryPercent != null && v.batteryPercent! > 0)
        .toList();
    final avgBat = withBat.isEmpty
        ? 0.0
        : withBat.map((v) => v.batteryPercent!).fold(0.0, (a, b) => a + b) /
            withBat.length;

    return summary.copyWith(averageBatteryPercent: avgBat);
  },
  name: 'fleetSummaryProvider',
);

final vehicleListProvider = FutureProvider<List<VehicleModel>>(
  (ref) => ref.watch(vehicleRepositoryProvider).getVehicles(),
  name: 'vehicleListProvider',
);

final criticalVehiclesProvider = FutureProvider<List<VehicleModel>>(
  (ref) => ref.watch(vehicleRepositoryProvider).getCriticalVehicles(),
  name: 'criticalVehiclesProvider',
);

final vehicleDetailProvider =
    FutureProvider.family<VehicleModel, String>(
  (ref, id) => ref.watch(vehicleRepositoryProvider).getVehicleById(id),
  name: 'vehicleDetailProvider',
);

// ── Real-time WebSocket providers ─────────────────────────────────────────────

/// Broadcasts every telemetry event received over the WebSocket (all vehicles).
final telemetryStreamProvider = StreamProvider<VehicleTelemetryModel>(
  (ref) => ref.watch(vehicleRepositoryProvider).watchTelemetry(),
  name: 'telemetryStreamProvider',
);

/// Filtered stream for a single vehicle — used by [VehicleDetailScreen].
final vehicleTelemetryProvider =
    StreamProvider.family<VehicleTelemetryModel, String>(
  (ref, vehicleId) =>
      ref.watch(vehicleRepositoryProvider).watchTelemetry(vehicleId: vehicleId),
  name: 'vehicleTelemetryProvider',
);

/// Alias consumed by [VehicleCard] — semantically identical to
/// [vehicleTelemetryProvider] but named to clarify intent ("latest snapshot").
/// Each card watches this independently so only the affected card rebuilds.
final latestTelemetryProvider =
    StreamProvider.family<VehicleTelemetryModel, String>(
  (ref, vehicleId) =>
      ref.watch(vehicleRepositoryProvider).watchTelemetry(vehicleId: vehicleId),
  name: 'latestTelemetryProvider',
);

/// Exposes the WebSocket connection lifecycle state.
/// Explicit type parameter avoids the `AsyncValue<Object?>` inference trap.
final wsConnectionStateProvider = StreamProvider<WsConnectionState>(
  (ref) => ref.watch(vehicleRepositoryProvider).connectionState,
  name: 'wsConnectionStateProvider',
);

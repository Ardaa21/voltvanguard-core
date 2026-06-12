/// Abstract vehicle repository — the only thing the presentation layer touches.
library;

import '../../../../core/network/websocket_service.dart';
import '../../../vehicle/data/models/vehicle_model.dart';
import '../../data/models/vehicle_telemetry_model.dart';

abstract interface class VehicleRepository {
  // ── REST ────────────────────────────────────────────────────────────────────
  Future<List<VehicleModel>>  getVehicles({int page, int size});
  Future<VehicleModel>        getVehicleById(String id);
  Future<List<VehicleModel>>  getCriticalVehicles();
  Future<FleetSummaryModel>   getFleetSummary();

  // ── Real-time ────────────────────────────────────────────────────────────────
  /// Emits a [VehicleTelemetryModel] every time the server pushes an update.
  /// Pass [vehicleId] to filter to a single vehicle; omit for all vehicles.
  Stream<VehicleTelemetryModel> watchTelemetry({String? vehicleId});

  Stream<WsConnectionState> get connectionState;
  void disconnect();
}

/// Concrete vehicle repository.
///
/// Two data sources:
///   1. REST via [ApiClient]  — initial load, fleet stats, pagination
///   2. WebSocket via [WebSocketService] — real-time telemetry patch stream
library;

import 'dart:async';

import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';
import '../../../../core/network/websocket_service.dart';
import '../../../vehicle/data/models/vehicle_model.dart';
import '../../domain/repositories/vehicle_repository.dart';
import '../models/vehicle_telemetry_model.dart';

class VehicleRepositoryImpl implements VehicleRepository {
  VehicleRepositoryImpl({
    required ApiClient apiClient,
    required WebSocketService wsService,
  })  : _api = apiClient,
        _ws  = wsService;

  final ApiClient        _api;
  final WebSocketService _ws;

  // ── REST ──────────────────────────────────────────────────────────────────

  @override
  Future<List<VehicleModel>> getVehicles({
    int page = 0,
    int size = ApiConstants.defaultPageSize,
  }) async {
    // Spring Boot returns Page<VehicleResponse> — a Map with a 'content' list.
    // Cast as Map and extract the 'content' array.
    final pageData = await _api.get<Map<String, dynamic>>(
      ApiConstants.vehicles,
      queryParameters: {'page': page, 'size': size, 'sort': 'createdAt'},
    );
    final content = (pageData?['content'] as List<dynamic>?) ?? [];
    return content
        .map((e) => VehicleModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<VehicleModel> getVehicleById(String id) async {
    final data = await _api.get<Map<String, dynamic>>(
      ApiConstants.vehicleById.replaceFirst('{id}', id),
    );
    return VehicleModel.fromJson(data!);
  }

  @override
  Future<List<VehicleModel>> getCriticalVehicles() async {
    final data = await _api.get<List<dynamic>>(ApiConstants.vehiclesCritical);
    return (data ?? [])
        .map((e) => VehicleModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<FleetSummaryModel> getFleetSummary() async {
    final data = await _api.get<Map<String, dynamic>>(
      ApiConstants.vehiclesFleetStats,
    );
    return FleetSummaryModel.fromJson(data!);
  }

  // ── WebSocket telemetry stream ─────────────────────────────────────────────

  @override
  Stream<VehicleTelemetryModel> watchTelemetry({String? vehicleId}) {
    _ws.connect();

    return _ws.stream
        .where((json) =>
            vehicleId == null || json['vehicleId'] == vehicleId)
        .map((json) => VehicleTelemetryModel.fromJson(json));
  }

  @override
  Stream<WsConnectionState> get connectionState => _ws.stateStream;

  @override
  void disconnect() => _ws.disconnect();
}

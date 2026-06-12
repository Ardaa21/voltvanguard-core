/// Real-time telemetry snapshot pushed over WebSocket.
///
/// Mirrors the Python [VehicleTelemetryEvent] Pydantic model and the
/// Java [VehicleTelemetryEvent] Kafka event — both produce the same JSON shape.
library;

import 'package:flutter/foundation.dart';

@immutable
class VehicleTelemetryModel {
  const VehicleTelemetryModel({
    required this.vehicleId,
    required this.timestamp,
    this.batteryPercent,
    this.latitude,
    this.longitude,
    this.speedKmh,
    this.estimatedRangeKm,
    this.batteryTemperatureCelsius,
    this.status,
    this.sequenceNumber,
    this.sourceType,
  });

  final String   vehicleId;
  final DateTime timestamp;
  final double?  batteryPercent;
  final double?  latitude;
  final double?  longitude;
  final double?  speedKmh;
  final double?  estimatedRangeKm;
  final double?  batteryTemperatureCelsius;
  final String?  status;
  final int?     sequenceNumber;
  final String?  sourceType; // "REAL" | "SIMULATED"

  // ── JSON ──────────────────────────────────────────────────────────────────

  factory VehicleTelemetryModel.fromJson(Map<String, dynamic> json) =>
      VehicleTelemetryModel(
        vehicleId:                json['vehicleId'] as String,
        timestamp:                DateTime.parse(json['timestamp'] as String),
        batteryPercent:           (json['batteryPercent'] as num?)?.toDouble(),
        latitude:                 (json['latitude'] as num?)?.toDouble(),
        longitude:                (json['longitude'] as num?)?.toDouble(),
        speedKmh:                 (json['speedKmh'] as num?)?.toDouble(),
        estimatedRangeKm:         (json['estimatedRangeKm'] as num?)?.toDouble(),
        batteryTemperatureCelsius:(json['batteryTemperatureCelsius'] as num?)?.toDouble(),
        status:                   json['status'] as String?,
        sequenceNumber:           json['sequenceNumber'] as int?,
        sourceType:               json['sourceType'] as String?,
      );

  // ── Helpers ───────────────────────────────────────────────────────────────

  bool get isCritical  => (batteryPercent ?? 100) <= 15;
  bool get isLow       => (batteryPercent ?? 100) <= 25;
  bool get isCharging  => status == 'CHARGING';
  bool get hasLocation => latitude != null && longitude != null;
  bool get isSimulated => sourceType == 'SIMULATED';

  VehicleTelemetryModel copyWith({
    double? batteryPercent,
    double? latitude,
    double? longitude,
    double? speedKmh,
    double? estimatedRangeKm,
    String? status,
  }) =>
      VehicleTelemetryModel(
        vehicleId:                vehicleId,
        timestamp:                DateTime.now(),
        batteryPercent:           batteryPercent ?? this.batteryPercent,
        latitude:                 latitude ?? this.latitude,
        longitude:                longitude ?? this.longitude,
        speedKmh:                 speedKmh ?? this.speedKmh,
        estimatedRangeKm:         estimatedRangeKm ?? this.estimatedRangeKm,
        batteryTemperatureCelsius:batteryTemperatureCelsius,
        status:                   status ?? this.status,
        sequenceNumber:           sequenceNumber,
        sourceType:               sourceType,
      );

  @override
  String toString() =>
      'VehicleTelemetry(vehicle=$vehicleId, bat=${batteryPercent?.toStringAsFixed(1)}%, '
      'status=$status, range=${estimatedRangeKm?.toStringAsFixed(1)}km)';
}

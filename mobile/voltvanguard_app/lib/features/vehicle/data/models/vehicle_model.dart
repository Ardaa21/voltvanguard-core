/// Data model — mirrors Spring Boot [ElectricVehicle] REST DTO exactly.
library;

import 'package:flutter/foundation.dart';

enum VehicleStatus {
  online,
  idle,
  charging,
  inTransit,
  awaitingTask,
  batteryCritical,
  offline,
  error,
  unknown;

  static VehicleStatus fromString(String? value) {
    // Backend sends SCREAMING_SNAKE_CASE (e.g. "BATTERY_CRITICAL").
    // Strip underscores before comparing so "BATTERY_CRITICAL" → "BATTERYCRITICAL"
    // matches Dart enum name "batteryCritical".toUpperCase() == "BATTERYCRITICAL".
    final normalized = (value ?? '').replaceAll('_', '').toUpperCase();
    return VehicleStatus.values.firstWhere(
      (e) => e.name.toUpperCase() == normalized,
      orElse: () => VehicleStatus.unknown,
    );
  }
}

@immutable
class VehicleModel {
  const VehicleModel({
    required this.id,
    required this.vin,
    required this.manufacturer,
    required this.model,
    required this.modelYear,
    required this.ownerId,
    required this.batteryCapacityKwh,
    this.batteryPercent,
    this.latitude,
    this.longitude,
    this.estimatedRangeKm,
    this.totalDistanceKm,
    required this.status,
    this.batteryLastUpdatedAt,
    this.locationLastUpdatedAt,
    this.createdAt,
    this.updatedAt,
  });

  final String        id;
  final String        vin;
  final String        manufacturer;
  final String        model;
  final int           modelYear;
  final String        ownerId;
  final double        batteryCapacityKwh;
  final double?       batteryPercent;
  final double?       latitude;
  final double?       longitude;
  final double?       estimatedRangeKm;
  final double?       totalDistanceKm;
  final VehicleStatus status;
  final DateTime?     batteryLastUpdatedAt;
  final DateTime?     locationLastUpdatedAt;
  final DateTime?     createdAt;
  final DateTime?     updatedAt;

  // ── JSON ──────────────────────────────────────────────────────────────────

  factory VehicleModel.fromJson(Map<String, dynamic> json) => VehicleModel(
    id:                   json['id'] as String,
    vin:                  json['vin'] as String,
    manufacturer:         json['manufacturer'] as String,
    model:                json['model'] as String,
    modelYear:            json['modelYear'] as int,
    ownerId:              json['ownerId'] as String,
    batteryCapacityKwh:   (json['batteryCapacityKwh'] as num).toDouble(),
    batteryPercent:       (json['batteryPercent'] as num?)?.toDouble(),
    latitude:             (json['latitude'] as num?)?.toDouble(),
    longitude:            (json['longitude'] as num?)?.toDouble(),
    estimatedRangeKm:     (json['estimatedRangeKm'] as num?)?.toDouble(),
    totalDistanceKm:      (json['totalDistanceKm'] as num?)?.toDouble(),
    status:               VehicleStatus.fromString(json['status'] as String?),
    batteryLastUpdatedAt: json['batteryLastUpdatedAt'] != null
        ? DateTime.parse(json['batteryLastUpdatedAt'] as String)
        : null,
    locationLastUpdatedAt: json['locationLastUpdatedAt'] != null
        ? DateTime.parse(json['locationLastUpdatedAt'] as String)
        : null,
    createdAt: json['createdAt'] != null
        ? DateTime.parse(json['createdAt'] as String)
        : null,
    updatedAt: json['updatedAt'] != null
        ? DateTime.parse(json['updatedAt'] as String)
        : null,
  );

  Map<String, dynamic> toJson() => {
    'id':                   id,
    'vin':                  vin,
    'manufacturer':         manufacturer,
    'model':                model,
    'modelYear':            modelYear,
    'ownerId':              ownerId,
    'batteryCapacityKwh':   batteryCapacityKwh,
    if (batteryPercent != null)    'batteryPercent':    batteryPercent,
    if (latitude != null)          'latitude':          latitude,
    if (longitude != null)         'longitude':         longitude,
    if (estimatedRangeKm != null)  'estimatedRangeKm':  estimatedRangeKm,
    if (totalDistanceKm != null)   'totalDistanceKm':   totalDistanceKm,
    'status': status.name.toUpperCase(),
  };

  // ── Domain helpers ────────────────────────────────────────────────────────

  bool get isBatteryCritical => (batteryPercent ?? 100) <= 15;
  bool get isOnline          => status == VehicleStatus.online;
  bool get isCharging        => status == VehicleStatus.charging;
  bool get hasLocation       => latitude != null && longitude != null;

  String get displayName     => '$manufacturer $model ($modelYear)';

  VehicleModel copyWith({
    double? batteryPercent,
    double? latitude,
    double? longitude,
    double? estimatedRangeKm,
    VehicleStatus? status,
    DateTime? batteryLastUpdatedAt,
  }) =>
      VehicleModel(
        id:                   id,
        vin:                  vin,
        manufacturer:         manufacturer,
        model:                model,
        modelYear:            modelYear,
        ownerId:              ownerId,
        batteryCapacityKwh:   batteryCapacityKwh,
        batteryPercent:       batteryPercent ?? this.batteryPercent,
        latitude:             latitude ?? this.latitude,
        longitude:            longitude ?? this.longitude,
        estimatedRangeKm:     estimatedRangeKm ?? this.estimatedRangeKm,
        totalDistanceKm:      totalDistanceKm,
        status:               status ?? this.status,
        batteryLastUpdatedAt: batteryLastUpdatedAt ?? this.batteryLastUpdatedAt,
        locationLastUpdatedAt: locationLastUpdatedAt,
        createdAt:            createdAt,
        updatedAt:            updatedAt,
      );
}

// ── Fleet summary ─────────────────────────────────────────────────────────────

@immutable
class FleetSummaryModel {
  const FleetSummaryModel({
    required this.totalVehicles,
    required this.onlineVehicles,
    required this.chargingVehicles,
    required this.criticalVehicles,
    required this.offlineVehicles,
    required this.averageBatteryPercent,
  });

  final int    totalVehicles;
  final int    onlineVehicles;
  final int    chargingVehicles;
  final int    criticalVehicles;
  final int    offlineVehicles;
  final double averageBatteryPercent;

  factory FleetSummaryModel.fromJson(Map<String, dynamic> json) {
    // Backend returns Map<VehicleStatus, Long> serialised as:
    //   {"ONLINE": 5, "OFFLINE": 3, "CHARGING": 2, "BATTERY_CRITICAL": 1,
    //    "IDLE": 2, "IN_TRANSIT": 3, "AWAITING_TASK": 1}
    // There is no totalVehicles / averageBatteryPercent field — compute them here.
    final online       = (json['ONLINE']           as num?)?.toInt() ?? 0;
    final offline      = (json['OFFLINE']          as num?)?.toInt() ?? 0;
    final charging     = (json['CHARGING']         as num?)?.toInt() ?? 0;
    final critical     = (json['BATTERY_CRITICAL'] as num?)?.toInt() ?? 0;
    final idle         = (json['IDLE']             as num?)?.toInt() ?? 0;
    final inTransit    = (json['IN_TRANSIT']       as num?)?.toInt() ?? 0;
    final awaitingTask = (json['AWAITING_TASK']    as num?)?.toInt() ?? 0;
    final error        = (json['ERROR']            as num?)?.toInt() ?? 0;
    return FleetSummaryModel(
      totalVehicles:         online + offline + charging + critical + idle + inTransit + awaitingTask + error,
      onlineVehicles:        online + idle + inTransit + awaitingTask,
      chargingVehicles:      charging,
      criticalVehicles:      critical,
      offlineVehicles:       offline,
      averageBatteryPercent: 0.0, // filled in by fleetSummaryProvider from vehicle list
    );
  }

  FleetSummaryModel copyWith({
    int?    totalVehicles,
    int?    onlineVehicles,
    int?    chargingVehicles,
    int?    criticalVehicles,
    int?    offlineVehicles,
    double? averageBatteryPercent,
  }) =>
      FleetSummaryModel(
        totalVehicles:         totalVehicles         ?? this.totalVehicles,
        onlineVehicles:        onlineVehicles        ?? this.onlineVehicles,
        chargingVehicles:      chargingVehicles      ?? this.chargingVehicles,
        criticalVehicles:      criticalVehicles      ?? this.criticalVehicles,
        offlineVehicles:       offlineVehicles       ?? this.offlineVehicles,
        averageBatteryPercent: averageBatteryPercent ?? this.averageBatteryPercent,
      );
}

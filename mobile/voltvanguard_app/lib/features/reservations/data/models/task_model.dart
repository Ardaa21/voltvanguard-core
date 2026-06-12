/// Data model — mirrors the Spring Boot [AutonomousTask] REST DTO.
library;

import 'dart:convert';

import 'package:flutter/foundation.dart';

enum TaskType {
  chargeScheduling,
  routeOptimization,
  diagnostics,
  fleetRebalancing,
  unknown;

  static TaskType fromString(String? s) => TaskType.values.firstWhere(
    (e) => e.name.toUpperCase().replaceAll('_', '') ==
        (s ?? '').toUpperCase().replaceAll('_', ''),
    orElse: () => TaskType.unknown,
  );

  String get displayName => switch (this) {
    TaskType.chargeScheduling  => 'Charge Scheduling',
    TaskType.routeOptimization => 'Route Optimization',
    TaskType.diagnostics       => 'Diagnostics',
    TaskType.fleetRebalancing  => 'Fleet Rebalancing',
    TaskType.unknown           => 'Unknown',
  };
}

enum TaskStatus {
  pending,
  inProgress,
  completed,
  failed,
  cancelled,
  expired,
  unknown;

  static TaskStatus fromString(String? s) => TaskStatus.values.firstWhere(
    (e) => e.name.toUpperCase().replaceAll('_', '') ==
        (s ?? '').toUpperCase().replaceAll('_', ''),
    orElse: () => TaskStatus.unknown,
  );

  bool get isTerminal => this == TaskStatus.completed ||
      this == TaskStatus.failed ||
      this == TaskStatus.cancelled ||
      this == TaskStatus.expired;

  bool get isActive => this == TaskStatus.pending ||
      this == TaskStatus.inProgress;
}

// ── Parsed reservation payload (from JSON blob in task.payload) ───────────────

@immutable
class ReservationPayload {
  const ReservationPayload({
    required this.stationId,
    required this.stationName,
    required this.stationLatitude,
    required this.stationLongitude,
    required this.vehicleBatteryPercent,
    required this.requestedChargeToPercent,
    required this.urgency,
    required this.reasoning,
    required this.agentId,
    this.estimatedRangeKm,
  });

  final String  stationId;
  final String  stationName;
  final double  stationLatitude;
  final double  stationLongitude;
  final double  vehicleBatteryPercent;
  final int     requestedChargeToPercent;
  final String  urgency;
  final String  reasoning;
  final String  agentId;
  final double? estimatedRangeKm;

  factory ReservationPayload.fromJson(Map<String, dynamic> json) =>
      ReservationPayload(
        stationId:                json['station_id'] as String,
        stationName:              json['station_name'] as String,
        stationLatitude:          (json['station_latitude'] as num).toDouble(),
        stationLongitude:         (json['station_longitude'] as num).toDouble(),
        vehicleBatteryPercent:    (json['vehicle_battery_percent'] as num).toDouble(),
        requestedChargeToPercent: (json['requested_charge_to_pct'] as num).toInt(),
        urgency:                  json['urgency'] as String,
        reasoning:                json['reasoning'] as String,
        agentId:                  json['agent_id'] as String,
        estimatedRangeKm:         (json['estimated_range_km'] as num?)?.toDouble(),
      );
}

// ── Task model ─────────────────────────────────────────────────────────────────

@immutable
class TaskModel {
  const TaskModel({
    required this.id,
    required this.vehicleId,
    required this.taskType,
    required this.status,
    required this.priority,
    this.scheduledAt,
    this.startedAt,
    this.completedAt,
    this.expiresAt,
    this.payloadJson,
    this.errorMessage,
    this.agentId,
    this.createdAt,
    this.updatedAt,
  });

  final String     id;
  final String     vehicleId;
  final TaskType   taskType;
  final TaskStatus status;
  final int        priority;
  final DateTime?  scheduledAt;
  final DateTime?  startedAt;
  final DateTime?  completedAt;
  final DateTime?  expiresAt;
  final String?    payloadJson;
  final String?    errorMessage;
  final String?    agentId;
  final DateTime?  createdAt;
  final DateTime?  updatedAt;

  // ── JSON ───────────────────────────────────────────────────────────────────

  factory TaskModel.fromJson(Map<String, dynamic> json) => TaskModel(
    id:           json['id'] as String,
    vehicleId:    json['vehicleId'] as String,
    taskType:     TaskType.fromString(json['taskType'] as String?),
    status:       TaskStatus.fromString(json['status'] as String?),
    priority:     (json['priority'] as num).toInt(),
    scheduledAt:  _dt(json['scheduledAt']),
    startedAt:    _dt(json['startedAt']),
    completedAt:  _dt(json['completedAt']),
    expiresAt:    _dt(json['expiresAt']),
    payloadJson:  json['payload'] as String?,
    errorMessage: json['errorMessage'] as String?,
    agentId:      json['agentId'] as String?,
    createdAt:    _dt(json['createdAt']),
    updatedAt:    _dt(json['updatedAt']),
  );

  static DateTime? _dt(dynamic v) =>
      v == null ? null : DateTime.parse(v as String);

  // ── Helpers ────────────────────────────────────────────────────────────────

  /// Parses the JSON payload blob for CHARGE_SCHEDULING tasks.
  ReservationPayload? get reservationPayload {
    if (taskType != TaskType.chargeScheduling || payloadJson == null) {
      return null;
    }
    try {
      return ReservationPayload.fromJson(
        json.decode(payloadJson!) as Map<String, dynamic>,
      );
    } catch (_) {
      return null;
    }
  }

  bool get isExpired {
    if (expiresAt == null) return false;
    return expiresAt!.isBefore(DateTime.now());
  }

  String get priorityLabel => switch (priority) {
    1 => 'Critical',
    2 => 'High',
    3 => 'Medium',
    4 => 'Low',
    _ => 'P$priority',
  };
}

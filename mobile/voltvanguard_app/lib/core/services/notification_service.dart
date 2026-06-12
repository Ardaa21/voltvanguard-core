/// Unified notification façade.
///
/// Android  → flutter_local_notifications (high-priority channel)
/// iOS      → flutter_local_notifications + Live Activity via MethodChannel
///
/// Live Activities (iOS 16.1+):
///   Dart sends start/update/end events over the 'voltvanguard/live_activity'
///   MethodChannel.  The native Swift side (VoltVanguardLiveActivity.swift)
///   receives them and calls ActivityKit accordingly.
library;

import 'dart:developer' as developer;
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

// ── Channel & notification ID constants ──────────────────────────────────────

const _liveActivityChannel = MethodChannel('voltvanguard/live_activity');

const _androidChannelId   = 'voltvanguard_reservations';
const _androidChannelName = 'Autonomous Reservations';
const _androidChannelDesc = 'Alerts when the AI agent creates a charging reservation';

const _notifIdReservation = 1001;
const _notifIdAlert       = 1002;

// ── Notification payload model ─────────────────────────────────────────────

class ReservationNotificationPayload {
  const ReservationNotificationPayload({
    required this.taskId,
    required this.vehicleId,
    required this.stationName,
    required this.urgency,
    required this.batteryPct,
    this.estimatedRangeKm,
  });

  final String  taskId;
  final String  vehicleId;
  final String  stationName;
  final String  urgency;        // "critical" | "high" | "medium"
  final double  batteryPct;
  final double? estimatedRangeKm;

  Map<String, dynamic> toMap() => {
    'taskId':            taskId,
    'vehicleId':         vehicleId,
    'stationName':       stationName,
    'urgency':           urgency,
    'batteryPct':        batteryPct,
    'estimatedRangeKm':  estimatedRangeKm,
  };
}

// ── Service ───────────────────────────────────────────────────────────────────

class NotificationService {
  static final NotificationService _instance = NotificationService._();
  factory NotificationService() => _instance;
  NotificationService._();

  final _plugin = FlutterLocalNotificationsPlugin();
  bool _initialised = false;

  // ── Init ──────────────────────────────────────────────────────────────────

  Future<void> init() async {
    if (_initialised) return;

    const initSettingsAndroid =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    const initSettingsIOS = DarwinInitializationSettings(
      requestAlertPermission:  true,
      requestBadgePermission:  true,
      requestSoundPermission:  true,
    );

    const initSettings = InitializationSettings(
      android: initSettingsAndroid,
      iOS:     initSettingsIOS,
    );

    await _plugin.initialize(
      initSettings,
      onDidReceiveNotificationResponse: _onNotificationTap,
    );

    if (Platform.isAndroid) {
      await _createAndroidChannel();
    }

    _initialised = true;
    developer.log('NotificationService initialised ✓', name: 'NotificationService');
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /// Show a local notification when the AI agent books a charging slot.
  Future<void> showReservationNotification(
    ReservationNotificationPayload payload,
  ) async {
    await _ensureInit();

    final title = _urgencyTitle(payload.urgency);
    final body  = '${payload.stationName} reserved · '
        'Battery: ${payload.batteryPct.toStringAsFixed(0)}%'
        '${payload.estimatedRangeKm != null ? ' · ~${payload.estimatedRangeKm!.toStringAsFixed(0)} km' : ''}';

    final androidDetails = AndroidNotificationDetails(
      _androidChannelId,
      _androidChannelName,
      channelDescription: _androidChannelDesc,
      importance:         Importance.max,
      priority:           Priority.high,
      styleInformation:   BigTextStyleInformation(body),
      color:              const Color(0xFF00D4AA),
      icon:               '@drawable/ic_bolt',
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentSound: true,
      presentBadge: true,
      interruptionLevel: InterruptionLevel.timeSensitive,
    );

    final details = NotificationDetails(
      android: androidDetails,
      iOS:     iosDetails,
    );

    await _plugin.show(_notifIdReservation, title, body, details);
    developer.log(
      'Local notification shown: $title',
      name: 'NotificationService',
    );

    // Also start a Live Activity on iOS
    if (Platform.isIOS) {
      await startLiveActivity(payload);
    }
  }

  /// Show a generic critical battery alert.
  Future<void> showCriticalBatteryAlert({
    required String vehicleId,
    required double batteryPct,
  }) async {
    await _ensureInit();

    const androidDetails = AndroidNotificationDetails(
      _androidChannelId,
      _androidChannelName,
      importance: Importance.max,
      priority:   Priority.high,
      color:      Color(0xFFEF4444),
    );

    await _plugin.show(
      _notifIdAlert,
      '⚡ Battery Critical',
      'Vehicle $vehicleId is at ${batteryPct.toStringAsFixed(0)}% — AI agent is searching for a station.',
      const NotificationDetails(android: androidDetails),
    );
  }

  // ── iOS Live Activities ───────────────────────────────────────────────────

  /// Start a Live Activity lock-screen widget for an autonomous reservation.
  Future<void> startLiveActivity(ReservationNotificationPayload payload) async {
    if (!Platform.isIOS) return;
    try {
      await _liveActivityChannel.invokeMethod<void>(
        'startActivity',
        payload.toMap(),
      );
      developer.log(
        'Live Activity started for task ${payload.taskId}',
        name: 'NotificationService',
      );
    } on PlatformException catch (e) {
      developer.log(
        'Live Activity start failed: ${e.message}',
        name: 'NotificationService',
      );
    }
  }

  /// Update an in-progress Live Activity (e.g. vehicle en-route to station).
  Future<void> updateLiveActivity({
    required String taskId,
    required double batteryPct,
    required String statusMessage,
  }) async {
    if (!Platform.isIOS) return;
    try {
      await _liveActivityChannel.invokeMethod<void>('updateActivity', {
        'taskId':        taskId,
        'batteryPct':    batteryPct,
        'statusMessage': statusMessage,
      });
    } on PlatformException catch (e) {
      developer.log(
        'Live Activity update failed: ${e.message}',
        name: 'NotificationService',
      );
    }
  }

  /// End the Live Activity when the task is terminal (completed / failed).
  Future<void> endLiveActivity(String taskId) async {
    if (!Platform.isIOS) return;
    try {
      await _liveActivityChannel.invokeMethod<void>(
        'endActivity',
        {'taskId': taskId},
      );
    } on PlatformException catch (e) {
      developer.log(
        'Live Activity end failed: ${e.message}',
        name: 'NotificationService',
      );
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  Future<void> _ensureInit() async {
    if (!_initialised) await init();
  }

  Future<void> _createAndroidChannel() async {
    const channel = AndroidNotificationChannel(
      _androidChannelId,
      _androidChannelName,
      description: _androidChannelDesc,
      importance:  Importance.max,
      playSound:   true,
    );
    await _plugin
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.createNotificationChannel(channel);
  }

  void _onNotificationTap(NotificationResponse response) {
    developer.log(
      'Notification tapped: payload=${response.payload}',
      name: 'NotificationService',
    );
    // Navigation is handled via go_router using the payload string (taskId).
  }

  String _urgencyTitle(String urgency) => switch (urgency.toLowerCase()) {
    'critical' => '🔴 CRITICAL — Charging Reservation Created',
    'high'     => '🟠 Charging Reservation Created',
    _          => '🟢 Charging Reservation Created',
  };
}

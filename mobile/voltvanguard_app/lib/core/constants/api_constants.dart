/// Central place for every network constant.
///
/// Base URL resolution at runtime:
///   iOS Simulator   → localhost resolves to the Mac host directly ✓
///   Android Emulator → 10.0.2.2 is the special alias for the host loopback
///   Physical device  → set VOLTVANGUARD_BASE_URL env var or edit [defaultBaseUrl]
library;

import 'dart:io';

class ApiConstants {
  ApiConstants._();

  // ── Base URLs ────────────────────────────────────────────────────────────────
  //
  // iOS Simulator shares the Mac's network stack, so localhost works directly.
  // Android Emulator uses 10.0.2.2 to reach the host machine.
  //
  static String get defaultBaseUrl {
    if (Platform.isAndroid) {
      // Trailing slash is REQUIRED for Dio URI resolution.
      // Without it, path '/vehicles' drops '/api/v1' → hits wrong root endpoint.
      return 'http://10.0.2.2:8080/api/v1/';
    }
    return 'http://localhost:8080/api/v1/';
  }

  static String get defaultWsUrl {
    if (Platform.isAndroid) {
      return 'ws://10.0.2.2:8080/api/v1/ws/telemetry';
    }
    return 'ws://localhost:8080/api/v1/ws/telemetry';
  }

  // ── REST endpoints ───────────────────────────────────────────────────────────
  static const String vehicles           = '/vehicles';
  static const String vehicleById        = '/vehicles/{id}';
  static const String vehicleTelemetry   = '/vehicles/{id}/telemetry';
  static const String vehiclesCritical   = '/vehicles/alerts/critical';
  static const String vehiclesFleetStats = '/vehicles/analytics/fleet-summary';

  static const String stations           = '/stations';
  static const String stationsNearby     = '/stations/nearby/available';

  static const String tasks              = '/tasks';
  static const String taskById           = '/tasks/{id}';
  static const String tasksClaim         = '/tasks/claim';

  // ── Timeouts ─────────────────────────────────────────────────────────────────
  static const Duration connectTimeout   = Duration(seconds: 10);
  static const Duration receiveTimeout   = Duration(seconds: 15);
  static const Duration sendTimeout      = Duration(seconds: 10);

  // ── WebSocket reconnect backoff ───────────────────────────────────────────────
  /// 2 s → 4 s → 8 s → … capped at [wsMaxReconnectDelay].
  static const Duration wsInitialReconnectDelay = Duration(seconds: 2);
  static const Duration wsMaxReconnectDelay      = Duration(seconds: 30);

  // ── Pagination ────────────────────────────────────────────────────────────────
  static const int defaultPageSize = 20;

  // ── SharedPreferences keys ────────────────────────────────────────────────────
  static const String prefLastVehicleId = 'last_vehicle_id';
  static const String prefBaseUrl       = 'base_url';
}

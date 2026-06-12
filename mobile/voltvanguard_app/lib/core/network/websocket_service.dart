/// Resilient WebSocket client for the VoltVanguard telemetry stream.
///
/// Connects to ws://<host>/api/v1/ws/telemetry and emits decoded
/// [Map<String, dynamic>] payloads through the [stream] getter.
///
/// Reconnection strategy:
///   Exponential backoff starting at [ApiConstants.wsInitialReconnectDelay],
///   doubling on each attempt until [ApiConstants.wsMaxReconnectDelay].
///   The delay resets after a stable connection (> 30 s uptime).
library;

import 'dart:async';
import 'dart:convert';
import 'dart:developer' as developer;

import 'package:web_socket_channel/web_socket_channel.dart';

import '../constants/api_constants.dart';

enum WsConnectionState { disconnected, connecting, connected, reconnecting }

class WebSocketService {
  WebSocketService({String? wsUrl})
      : _wsUrl = wsUrl ?? ApiConstants.defaultWsUrl;

  final String _wsUrl;

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _subscription;
  Timer? _reconnectTimer;

  Duration _reconnectDelay = ApiConstants.wsInitialReconnectDelay;
  DateTime?  _connectedAt;

  // ── Public streams ────────────────────────────────────────────────────────

  final _dataController  = StreamController<Map<String, dynamic>>.broadcast();
  final _stateController = StreamController<WsConnectionState>.broadcast();

  /// Raw decoded JSON payloads from the server.
  Stream<Map<String, dynamic>> get stream => _dataController.stream;

  /// Connection lifecycle events.
  Stream<WsConnectionState> get stateStream => _stateController.stream;

  WsConnectionState _state = WsConnectionState.disconnected;
  WsConnectionState get state => _state;

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  /// Establish the WebSocket connection. Idempotent.
  void connect() {
    if (_state == WsConnectionState.connected ||
        _state == WsConnectionState.connecting) return;
    _connect();
  }

  void disconnect() {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _subscription?.cancel();
    _channel?.sink.close();
    _channel = null;
    _setState(WsConnectionState.disconnected);
    developer.log('WebSocket disconnected (manual)', name: 'WebSocketService');
  }

  void dispose() {
    disconnect();
    _dataController.close();
    _stateController.close();
  }

  // ── Core ──────────────────────────────────────────────────────────────────

  void _connect() {
    _setState(
      _state == WsConnectionState.disconnected
          ? WsConnectionState.connecting
          : WsConnectionState.reconnecting,
    );

    developer.log(
      'WebSocket connecting → $_wsUrl (backoff: ${_reconnectDelay.inSeconds}s)',
      name: 'WebSocketService',
    );

    try {
      _channel = WebSocketChannel.connect(Uri.parse(_wsUrl));
    } catch (e) {
      developer.log('WebSocket connect failed: $e', name: 'WebSocketService');
      _scheduleReconnect();
      return;
    }

    _subscription = _channel!.stream.listen(
      _onData,
      onError: _onError,
      onDone:  _onDone,
      cancelOnError: false,
    );

    _setState(WsConnectionState.connected);
    _connectedAt = DateTime.now();
    _reconnectDelay = ApiConstants.wsInitialReconnectDelay; // reset on success
    developer.log('WebSocket connected ✓', name: 'WebSocketService');
  }

  void _onData(dynamic raw) {
    try {
      final decoded = json.decode(raw as String) as Map<String, dynamic>;
      _dataController.add(decoded);
    } catch (e) {
      developer.log('WebSocket decode error: $e', name: 'WebSocketService');
    }
  }

  void _onError(Object error) {
    developer.log('WebSocket error: $error', name: 'WebSocketService');
    _scheduleReconnect();
  }

  void _onDone() {
    developer.log('WebSocket closed', name: 'WebSocketService');
    // Only auto-reconnect if not manually disconnected
    if (_state != WsConnectionState.disconnected) {
      _scheduleReconnect();
    }
  }

  void _scheduleReconnect() {
    _subscription?.cancel();
    _channel = null;

    _setState(WsConnectionState.reconnecting);

    developer.log(
      'WebSocket reconnecting in ${_reconnectDelay.inSeconds}s',
      name: 'WebSocketService',
    );

    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(_reconnectDelay, _connect);

    // Exponential backoff capped at max
    final nextDelay = _reconnectDelay * 2;
    _reconnectDelay = nextDelay > ApiConstants.wsMaxReconnectDelay
        ? ApiConstants.wsMaxReconnectDelay
        : nextDelay;
  }

  void _setState(WsConnectionState newState) {
    _state = newState;
    if (!_stateController.isClosed) {
      _stateController.add(newState);
    }
  }
}

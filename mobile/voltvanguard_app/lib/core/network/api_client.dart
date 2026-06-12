/// Dio-based HTTP client wired to the VoltVanguard Spring Boot backend.
///
/// Features:
///   • Base URL + timeout configuration from [ApiConstants]
///   • Request/response logging interceptor (debug only)
///   • Automatic retry on 5xx / network errors (up to 3 attempts)
///   • Centralised DioException → [AppException] mapping
library;

import 'dart:developer' as developer;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../constants/api_constants.dart';

// ── App-level exception types ─────────────────────────────────────────────────

sealed class AppException implements Exception {
  const AppException(this.message);
  final String message;

  @override
  String toString() => 'AppException: $message';
}

final class NetworkException extends AppException {
  const NetworkException(super.message);
}

final class ServerException extends AppException {
  const ServerException(super.message, {this.statusCode});
  final int? statusCode;
}

final class NotFoundException extends AppException {
  const NotFoundException(super.message);
}

final class ValidationException extends AppException {
  const ValidationException(super.message);
}

final class UnauthorizedException extends AppException {
  const UnauthorizedException([super.message = 'Unauthorised']);
}

// ── Logging interceptor ───────────────────────────────────────────────────────

class _LoggingInterceptor extends Interceptor {
  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    developer.log(
      '→ ${options.method} ${options.uri}',
      name: 'ApiClient',
    );
    handler.next(options);
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    developer.log(
      '← ${response.statusCode} ${response.requestOptions.uri}',
      name: 'ApiClient',
    );
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    developer.log(
      '✗ ${err.type.name} ${err.requestOptions.uri} — ${err.message}',
      name:  'ApiClient',
      error: err,
    );
    handler.next(err);
  }
}

// ── Retry interceptor ─────────────────────────────────────────────────────────

class _RetryInterceptor extends Interceptor {
  _RetryInterceptor(this._dio);

  final Dio _dio;
  static const int _maxAttempts = 3;

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final options = err.requestOptions;
    final attempt = (options.extra['_attempt'] as int?) ?? 1;

    final shouldRetry = attempt < _maxAttempts &&
        (err.type == DioExceptionType.connectionError ||
            err.type == DioExceptionType.receiveTimeout ||
            (err.response?.statusCode != null &&
                err.response!.statusCode! >= 500));

    if (!shouldRetry) {
      handler.next(err);
      return;
    }

    await Future<void>.delayed(Duration(seconds: attempt * 2));

    try {
      final response = await _dio.fetch<dynamic>(
        options.copyWith(extra: {...options.extra, '_attempt': attempt + 1}),
      );
      handler.resolve(response);
    } on DioException catch (e) {
      handler.next(e);
    }
  }
}

// ── Api Client ────────────────────────────────────────────────────────────────

class ApiClient {
  ApiClient({String? baseUrl}) {
    _dio = Dio(
      BaseOptions(
        baseUrl:        baseUrl ?? ApiConstants.defaultBaseUrl,
        connectTimeout: ApiConstants.connectTimeout,
        receiveTimeout: ApiConstants.receiveTimeout,
        sendTimeout:    ApiConstants.sendTimeout,
        headers: const {
          'Content-Type': 'application/json',
          'Accept':       'application/json',
        },
      ),
    );

    if (kDebugMode) {
      _dio.interceptors.add(_LoggingInterceptor());
    }
    _dio.interceptors.add(_RetryInterceptor(_dio));
  }

  late final Dio _dio;

  // ── Generic request helpers ────────────────────────────────────────────────

  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    T Function(dynamic json)? fromJson,
  }) =>
      _request<T>(
        'GET',
        path,
        queryParameters: queryParameters,
        fromJson: fromJson,
      );

  Future<T> post<T>(
    String path, {
    dynamic data,
    T Function(dynamic json)? fromJson,
  }) =>
      _request<T>('POST', path, data: data, fromJson: fromJson);

  Future<T> patch<T>(
    String path, {
    dynamic data,
    T Function(dynamic json)? fromJson,
  }) =>
      _request<T>('PATCH', path, data: data, fromJson: fromJson);

  Future<void> delete(String path) =>
      _request<void>('DELETE', path);

  // ── Core ──────────────────────────────────────────────────────────────────

  Future<T> _request<T>(
    String method,
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    T Function(dynamic json)? fromJson,
  }) async {
    // Strip the leading '/' so Dio resolves relative to baseUrl (which ends
    // with '/').  Without this, '/vehicles' would be treated as an absolute
    // path from the host root and '/api/v1' would be silently dropped.
    final cleanPath = path.startsWith('/') ? path.substring(1) : path;

    try {
      final response = await _dio.request<Map<String, dynamic>>(
        cleanPath,
        data:            data,
        queryParameters: queryParameters,
        options:         Options(method: method),
      );

      // Unwrap the ApiResponse<T> envelope from Spring Boot
      final envelope = response.data;
      if (envelope == null) return (fromJson?.call(null) as T);

      final success = envelope['success'] as bool? ?? false;
      if (!success) {
        throw ServerException(
          envelope['error'] as String? ?? 'Unknown server error',
          statusCode: response.statusCode,
        );
      }

      final payload = envelope['data'];
      if (fromJson != null) return fromJson(payload);
      return payload as T;
    } on DioException catch (e) {
      throw _mapDioException(e);
    }
  }

  AppException _mapDioException(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionError:
      case DioExceptionType.connectionTimeout:
        return const NetworkException('Cannot reach server. Check your connection.');
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.sendTimeout:
        return const NetworkException('Request timed out.');
      case DioExceptionType.badResponse:
        final status = e.response?.statusCode ?? 0;
        final body   = e.response?.data;
        final msg    = (body is Map ? body['error'] as String? : null) ??
            e.message ?? 'Server error';
        return switch (status) {
          401 => const UnauthorizedException(),
          404 => NotFoundException(msg),
          422 => ValidationException(msg),
          _   => ServerException(msg, statusCode: status),
        };
      default:
        return NetworkException(e.message ?? 'Unexpected network error');
    }
  }
}

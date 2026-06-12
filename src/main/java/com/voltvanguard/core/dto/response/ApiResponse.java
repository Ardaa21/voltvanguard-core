package com.voltvanguard.core.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Uniform envelope for all API responses.
 *
 * <p>Success: {@code {"success": true, "data": {...}, "timestamp": "..."}} <br>
 * Error:   {@code {"success": false, "error": "...", "timestamp": "..."}}</p>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T       data,
    String  error,
    String  message,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(Instant.now())
            .build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .timestamp(Instant.now())
            .build();
    }

    public static <T> ApiResponse<T> error(String error) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(error)
            .timestamp(Instant.now())
            .build();
    }
}

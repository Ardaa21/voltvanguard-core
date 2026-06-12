package com.voltvanguard.core.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Lightweight payload for real-time telemetry updates arriving from the IoT Gateway.
 * Only mutable telemetry fields are included — identity fields cannot be changed via telemetry.
 */
public record TelemetryUpdateRequest(

    @NotNull(message = "Battery percent is required")
    @DecimalMin("0.00") @DecimalMax("100.00")
    BigDecimal batteryPercent,

    @DecimalMin("-90.0") @DecimalMax("90.0")
    BigDecimal latitude,

    @DecimalMin("-180.0") @DecimalMax("180.0")
    BigDecimal longitude,

    @DecimalMin("0.0")
    BigDecimal estimatedRangeKm
) {}

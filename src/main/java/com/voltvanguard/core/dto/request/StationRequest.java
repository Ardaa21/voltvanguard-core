package com.voltvanguard.core.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Payload for registering or updating a ChargingStation.
 */
public record StationRequest(

    @NotBlank(message = "Station name is required")
    @Size(max = 150)
    String name,

    @Size(max = 300)
    String address,

    @Size(max = 100)
    String city,

    @Size(min = 2, max = 3)
    String countryCode,

    @NotNull(message = "Latitude is required")
    @DecimalMin("-90.0") @DecimalMax("90.0")
    BigDecimal latitude,

    @NotNull(message = "Longitude is required")
    @DecimalMin("-180.0") @DecimalMax("180.0")
    BigDecimal longitude,

    @NotNull(message = "Total connectors is required")
    @Min(1) @Max(500)
    Integer totalConnectors,

    @NotNull(message = "Power output is required")
    @DecimalMin("1.0") @DecimalMax("500.0")
    BigDecimal powerKw,

    @Min(0)
    Integer pricePerKwhCents,

    @Size(max = 100)
    String operatorName
) {}

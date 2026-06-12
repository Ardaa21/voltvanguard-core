package com.voltvanguard.core.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for registering or updating an ElectricVehicle.
 * Uses Java 21 record for immutable, concise DTO definition.
 */
public record VehicleRequest(

    @NotBlank(message = "VIN is required")
    @Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")
    String vin,

    @NotBlank(message = "Manufacturer is required")
    @Size(max = 100)
    String manufacturer,

    @NotBlank(message = "Model is required")
    @Size(max = 100)
    String model,

    @NotNull(message = "Model year is required")
    @Min(2000) @Max(2100)
    Integer modelYear,

    @NotNull(message = "Owner ID is required")
    UUID ownerId,

    @NotNull(message = "Battery capacity is required")
    @DecimalMin("1.0") @DecimalMax("250.0")
    BigDecimal batteryCapacityKwh
) {}

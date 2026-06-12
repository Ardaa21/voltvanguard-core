package com.voltvanguard.core.dto.response;

import com.voltvanguard.core.entity.ElectricVehicle;
import com.voltvanguard.core.enums.VehicleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of ElectricVehicle returned to API consumers.
 * Static factory {@code from()} keeps mapping logic co-located with the DTO.
 */
public record VehicleResponse(
    UUID           id,
    String         vin,
    String         manufacturer,
    String         model,
    Integer        modelYear,
    UUID           ownerId,
    BigDecimal     batteryCapacityKwh,
    BigDecimal     batteryPercent,
    BigDecimal     latitude,
    BigDecimal     longitude,
    BigDecimal     estimatedRangeKm,
    BigDecimal     totalDistanceKm,
    VehicleStatus  status,
    boolean        isBatteryCritical,
    Instant        batteryLastUpdatedAt,
    Instant        locationLastUpdatedAt,
    Instant        createdAt,
    Instant        updatedAt
) {
    public static VehicleResponse from(ElectricVehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getVin(),
            vehicle.getManufacturer(),
            vehicle.getModel(),
            vehicle.getModelYear(),
            vehicle.getOwnerId(),
            vehicle.getBatteryCapacityKwh(),
            vehicle.getBatteryPercent(),
            vehicle.getLatitude(),
            vehicle.getLongitude(),
            vehicle.getEstimatedRangeKm(),
            vehicle.getTotalDistanceKm(),
            vehicle.getStatus(),
            vehicle.isBatteryCritical(),
            vehicle.getBatteryLastUpdatedAt(),
            vehicle.getLocationLastUpdatedAt(),
            vehicle.getCreatedAt(),
            vehicle.getUpdatedAt()
        );
    }
}

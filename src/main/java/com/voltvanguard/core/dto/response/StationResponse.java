package com.voltvanguard.core.dto.response;

import com.voltvanguard.core.entity.ChargingStation;
import com.voltvanguard.core.enums.StationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of ChargingStation returned to API consumers.
 * The {@code occupancyRate} is computed on mapping, not stored in DB.
 */
public record StationResponse(
    UUID          id,
    String        name,
    String        address,
    String        city,
    String        countryCode,
    BigDecimal    latitude,
    BigDecimal    longitude,
    Integer       totalConnectors,
    Integer       availableConnectors,
    BigDecimal    powerKw,
    Integer       pricePerKwhCents,
    String        operatorName,
    StationStatus status,
    double        occupancyRate,
    Instant       createdAt,
    Instant       updatedAt
) {
    public static StationResponse from(ChargingStation station) {
        return new StationResponse(
            station.getId(),
            station.getName(),
            station.getAddress(),
            station.getCity(),
            station.getCountryCode(),
            station.getLatitude(),
            station.getLongitude(),
            station.getTotalConnectors(),
            station.getAvailableConnectors(),
            station.getPowerKw(),
            station.getPricePerKwhCents(),
            station.getOperatorName(),
            station.getStatus(),
            station.getOccupancyRate(),
            station.getCreatedAt(),
            station.getUpdatedAt()
        );
    }
}

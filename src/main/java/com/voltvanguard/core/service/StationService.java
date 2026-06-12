package com.voltvanguard.core.service;

import com.voltvanguard.core.dto.request.StationRequest;
import com.voltvanguard.core.dto.response.StationResponse;
import com.voltvanguard.core.enums.StationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Contract for all ChargingStation business operations.
 */
public interface StationService {

    StationResponse create(StationRequest request);

    StationResponse findById(UUID id);

    Page<StationResponse> findAll(Pageable pageable);

    Page<StationResponse> findByStatus(StationStatus status, Pageable pageable);

    List<StationResponse> findAvailable();

    /**
     * Proximity search — returns stations within {@code radiusKm} of the given coordinates.
     * Results are ordered by distance (nearest first).
     */
    List<StationResponse> findNearby(BigDecimal latitude, BigDecimal longitude, double radiusKm);

    /**
     * Returns available stations near the given point — used by the Route Optimizer agent.
     */
    List<StationResponse> findAvailableNearby(BigDecimal latitude, BigDecimal longitude, double radiusKm);

    StationResponse update(UUID stationId, StationRequest request);

    StationResponse updateStatus(UUID stationId, StationStatus newStatus);

    /** Marks one connector as occupied; updates status to BUSY if fully occupied. */
    StationResponse occupyConnector(UUID stationId);

    /** Marks one connector as released; updates status to AVAILABLE if applicable. */
    StationResponse releaseConnector(UUID stationId);

    void delete(UUID stationId);
}

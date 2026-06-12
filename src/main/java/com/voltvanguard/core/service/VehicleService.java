package com.voltvanguard.core.service;

import com.voltvanguard.core.dto.request.TelemetryUpdateRequest;
import com.voltvanguard.core.dto.request.VehicleRequest;
import com.voltvanguard.core.dto.response.VehicleResponse;
import com.voltvanguard.core.enums.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Contract for all ElectricVehicle business operations.
 */
public interface VehicleService {

    VehicleResponse register(VehicleRequest request);

    VehicleResponse findById(UUID id);

    VehicleResponse findByVin(String vin);

    Page<VehicleResponse> findAll(Pageable pageable);

    Page<VehicleResponse> findByOwner(UUID ownerId, Pageable pageable);

    Page<VehicleResponse> findByStatus(VehicleStatus status, Pageable pageable);

    /** Accepts a live telemetry heartbeat and updates battery + location. */
    VehicleResponse updateTelemetry(UUID vehicleId, TelemetryUpdateRequest telemetry);

    VehicleResponse updateStatus(UUID vehicleId, VehicleStatus newStatus);

    VehicleResponse update(UUID vehicleId, VehicleRequest request);

    void deregister(UUID vehicleId);

    /** Returns vehicles whose battery is at or below the provided threshold. */
    List<VehicleResponse> findCriticalVehicles(BigDecimal batteryThreshold);

    /** Fleet-level status counts for the analytics dashboard. */
    java.util.Map<VehicleStatus, Long> getFleetStatusSummary();
}

package com.voltvanguard.core.service.impl;

import com.voltvanguard.core.dto.request.TelemetryUpdateRequest;
import com.voltvanguard.core.dto.request.VehicleRequest;
import com.voltvanguard.core.dto.response.VehicleResponse;
import com.voltvanguard.core.entity.ElectricVehicle;
import com.voltvanguard.core.enums.VehicleStatus;
import com.voltvanguard.core.exception.DuplicateResourceException;
import com.voltvanguard.core.exception.ResourceNotFoundException;
import com.voltvanguard.core.repository.ElectricVehicleRepository;
import com.voltvanguard.core.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic for Electric Vehicle management.
 *
 * <p>Cache strategy: individual vehicles are cached by UUID in Redis.
 * Telemetry updates use {@code @CachePut} to keep the cache warm without
 * an extra DB round-trip. Mutations that change vehicle identity evict the cache.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private static final String CACHE_VEHICLES = "vehicles";
    private static final BigDecimal CRITICAL_BATTERY_THRESHOLD = new BigDecimal("15.00");

    private final ElectricVehicleRepository vehicleRepository;

    // ── Write Operations ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public VehicleResponse register(VehicleRequest request) {
        log.info("Registering vehicle with VIN: {}", request.vin());

        if (vehicleRepository.existsByVin(request.vin())) {
            throw new DuplicateResourceException("Vehicle", "VIN", request.vin());
        }

        ElectricVehicle vehicle = ElectricVehicle.builder()
            .vin(request.vin())
            .manufacturer(request.manufacturer())
            .model(request.model())
            .modelYear(request.modelYear())
            .ownerId(request.ownerId())
            .batteryCapacityKwh(request.batteryCapacityKwh())
            .status(VehicleStatus.OFFLINE)
            .build();

        ElectricVehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle registered successfully: id={}, vin={}", saved.getId(), saved.getVin());
        return VehicleResponse.from(saved);
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_VEHICLES, key = "#vehicleId")
    public VehicleResponse updateTelemetry(UUID vehicleId, TelemetryUpdateRequest telemetry) {
        log.debug("Telemetry update for vehicle {}: battery={}%", vehicleId, telemetry.batteryPercent());

        ElectricVehicle vehicle = getOrThrow(vehicleId);

        vehicle.setBatteryPercent(telemetry.batteryPercent());
        vehicle.setBatteryLastUpdatedAt(Instant.now());

        if (telemetry.latitude() != null && telemetry.longitude() != null) {
            vehicle.setLatitude(telemetry.latitude());
            vehicle.setLongitude(telemetry.longitude());
            vehicle.setLocationLastUpdatedAt(Instant.now());
        }

        if (telemetry.estimatedRangeKm() != null) {
            vehicle.setEstimatedRangeKm(telemetry.estimatedRangeKm());
        }

        // Auto-elevate or recover status based on battery level
        if (vehicle.isBatteryCritical() && vehicle.getStatus() != VehicleStatus.CHARGING) {
            vehicle.setStatus(VehicleStatus.BATTERY_CRITICAL);
            log.warn("Vehicle {} battery critical: {}%", vehicleId, telemetry.batteryPercent());
        } else if (!vehicle.isBatteryCritical() && vehicle.getStatus() == VehicleStatus.BATTERY_CRITICAL) {
            vehicle.setStatus(VehicleStatus.ONLINE);
            log.info("Vehicle {} battery recovered: {}%", vehicleId, telemetry.batteryPercent());
        }

        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_VEHICLES, key = "#vehicleId")
    public VehicleResponse updateStatus(UUID vehicleId, VehicleStatus newStatus) {
        log.info("Status transition for vehicle {}: -> {}", vehicleId, newStatus);
        ElectricVehicle vehicle = getOrThrow(vehicleId);
        vehicle.setStatus(newStatus);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_VEHICLES, key = "#vehicleId")
    public VehicleResponse update(UUID vehicleId, VehicleRequest request) {
        ElectricVehicle vehicle = getOrThrow(vehicleId);

        // VIN change requires uniqueness check
        if (!vehicle.getVin().equals(request.vin()) && vehicleRepository.existsByVin(request.vin())) {
            throw new DuplicateResourceException("Vehicle", "VIN", request.vin());
        }

        vehicle.setVin(request.vin());
        vehicle.setManufacturer(request.manufacturer());
        vehicle.setModel(request.model());
        vehicle.setModelYear(request.modelYear());
        vehicle.setOwnerId(request.ownerId());
        vehicle.setBatteryCapacityKwh(request.batteryCapacityKwh());

        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_VEHICLES, key = "#vehicleId")
    public void deregister(UUID vehicleId) {
        ElectricVehicle vehicle = getOrThrow(vehicleId);
        vehicleRepository.delete(vehicle);
        log.info("Vehicle deregistered: {}", vehicleId);
    }

    // ── Read Operations ──────────────────────────────────────────────────────

    @Override
    @Cacheable(value = CACHE_VEHICLES, key = "#id")
    public VehicleResponse findById(UUID id) {
        return VehicleResponse.from(getOrThrow(id));
    }

    @Override
    public VehicleResponse findByVin(String vin) {
        return vehicleRepository.findByVin(vin)
            .map(VehicleResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "VIN", vin));
    }

    @Override
    public Page<VehicleResponse> findAll(Pageable pageable) {
        return vehicleRepository.findAll(pageable).map(VehicleResponse::from);
    }

    @Override
    public Page<VehicleResponse> findByOwner(UUID ownerId, Pageable pageable) {
        return vehicleRepository.findByOwnerId(ownerId, pageable).map(VehicleResponse::from);
    }

    @Override
    public Page<VehicleResponse> findByStatus(VehicleStatus status, Pageable pageable) {
        return vehicleRepository.findByStatus(status, pageable).map(VehicleResponse::from);
    }

    @Override
    public List<VehicleResponse> findCriticalVehicles(BigDecimal batteryThreshold) {
        BigDecimal threshold = batteryThreshold != null ? batteryThreshold : CRITICAL_BATTERY_THRESHOLD;
        return vehicleRepository.findVehiclesBelowBatteryThreshold(threshold)
            .stream()
            .map(VehicleResponse::from)
            .toList();
    }

    @Override
    public Map<VehicleStatus, Long> getFleetStatusSummary() {
        return vehicleRepository.countByStatus()
            .stream()
            .collect(Collectors.toMap(
                row -> (VehicleStatus) row[0],
                row -> (Long) row[1]
            ));
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private ElectricVehicle getOrThrow(UUID id) {
        return vehicleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id.toString()));
    }
}

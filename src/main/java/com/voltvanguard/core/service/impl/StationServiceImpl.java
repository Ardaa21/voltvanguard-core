package com.voltvanguard.core.service.impl;

import com.voltvanguard.core.dto.request.StationRequest;
import com.voltvanguard.core.dto.response.StationResponse;
import com.voltvanguard.core.entity.ChargingStation;
import com.voltvanguard.core.enums.StationStatus;
import com.voltvanguard.core.exception.ResourceNotFoundException;
import com.voltvanguard.core.repository.ChargingStationRepository;
import com.voltvanguard.core.service.StationService;
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
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for ChargingStation management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StationServiceImpl implements StationService {

    private static final String CACHE_STATIONS = "stations";

    private final ChargingStationRepository stationRepository;

    // ── Write Operations ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public StationResponse create(StationRequest request) {
        log.info("Creating charging station: {}", request.name());

        ChargingStation station = ChargingStation.builder()
            .name(request.name())
            .address(request.address())
            .city(request.city())
            .countryCode(request.countryCode())
            .latitude(request.latitude())
            .longitude(request.longitude())
            .totalConnectors(request.totalConnectors())
            .availableConnectors(request.totalConnectors())   // starts fully available
            .powerKw(request.powerKw())
            .pricePerKwhCents(request.pricePerKwhCents() != null ? request.pricePerKwhCents() : 0)
            .operatorName(request.operatorName())
            .status(StationStatus.AVAILABLE)
            .build();

        ChargingStation saved = stationRepository.save(station);
        log.info("Charging station created: id={}", saved.getId());
        return StationResponse.from(saved);
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_STATIONS, key = "#stationId")
    public StationResponse update(UUID stationId, StationRequest request) {
        ChargingStation station = getOrThrow(stationId);

        station.setName(request.name());
        station.setAddress(request.address());
        station.setCity(request.city());
        station.setCountryCode(request.countryCode());
        station.setLatitude(request.latitude());
        station.setLongitude(request.longitude());
        station.setPowerKw(request.powerKw());
        station.setOperatorName(request.operatorName());

        if (request.pricePerKwhCents() != null) {
            station.setPricePerKwhCents(request.pricePerKwhCents());
        }

        return StationResponse.from(stationRepository.save(station));
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_STATIONS, key = "#stationId")
    public StationResponse updateStatus(UUID stationId, StationStatus newStatus) {
        ChargingStation station = getOrThrow(stationId);
        station.setStatus(newStatus);
        return StationResponse.from(stationRepository.save(station));
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_STATIONS, key = "#stationId")
    public StationResponse occupyConnector(UUID stationId) {
        ChargingStation station = getOrThrow(stationId);
        station.occupyConnector();   // domain method handles business rules
        return StationResponse.from(stationRepository.save(station));
    }

    @Override
    @Transactional
    @CachePut(value = CACHE_STATIONS, key = "#stationId")
    public StationResponse releaseConnector(UUID stationId) {
        ChargingStation station = getOrThrow(stationId);
        station.releaseConnector();  // domain method handles business rules
        return StationResponse.from(stationRepository.save(station));
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_STATIONS, key = "#stationId")
    public void delete(UUID stationId) {
        ChargingStation station = getOrThrow(stationId);
        stationRepository.delete(station);
        log.info("Charging station deleted: {}", stationId);
    }

    // ── Read Operations ───────────────────────────────────────────────────────

    @Override
    @Cacheable(value = CACHE_STATIONS, key = "#id")
    public StationResponse findById(UUID id) {
        return StationResponse.from(getOrThrow(id));
    }

    @Override
    public Page<StationResponse> findAll(Pageable pageable) {
        return stationRepository.findAll(pageable).map(StationResponse::from);
    }

    @Override
    public Page<StationResponse> findByStatus(StationStatus status, Pageable pageable) {
        return stationRepository.findByStatus(status, pageable).map(StationResponse::from);
    }

    @Override
    public List<StationResponse> findAvailable() {
        return stationRepository.findAllAvailable()
            .stream()
            .map(StationResponse::from)
            .toList();
    }

    @Override
    public List<StationResponse> findNearby(BigDecimal latitude, BigDecimal longitude, double radiusKm) {
        return stationRepository.findNearby(latitude, longitude, radiusKm)
            .stream()
            .map(StationResponse::from)
            .toList();
    }

    @Override
    public List<StationResponse> findAvailableNearby(BigDecimal latitude, BigDecimal longitude, double radiusKm) {
        return stationRepository.findAvailableNearby(latitude, longitude, radiusKm)
            .stream()
            .map(StationResponse::from)
            .toList();
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private ChargingStation getOrThrow(UUID id) {
        return stationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChargingStation", "id", id.toString()));
    }
}

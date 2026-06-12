package com.voltvanguard.core.controller;

import com.voltvanguard.core.dto.request.StationRequest;
import com.voltvanguard.core.dto.response.ApiResponse;
import com.voltvanguard.core.dto.response.StationResponse;
import com.voltvanguard.core.enums.StationStatus;
import com.voltvanguard.core.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RESTful API for Charging Station management.
 * Base path: /api/v1/stations
 */
@RestController
@RequestMapping("/stations")
@RequiredArgsConstructor
@Tag(name = "Stations", description = "EV charging station grid management")
public class StationController {

    private final StationService stationService;

    @PostMapping
    @Operation(summary = "Register a new charging station")
    public ResponseEntity<ApiResponse<StationResponse>> create(
        @Valid @RequestBody StationRequest request
    ) {
        StationResponse response = stationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Station registered"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get station by UUID")
    public ResponseEntity<ApiResponse<StationResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.findById(id)));
    }

    @GetMapping
    @Operation(summary = "List all stations (paginated)")
    public ResponseEntity<ApiResponse<Page<StationResponse>>> findAll(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.findAll(pageable)));
    }

    @GetMapping("/available")
    @Operation(summary = "List all stations with available connectors")
    public ResponseEntity<ApiResponse<List<StationResponse>>> findAvailable() {
        return ResponseEntity.ok(ApiResponse.ok(stationService.findAvailable()));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find stations within a radius of given coordinates")
    public ResponseEntity<ApiResponse<List<StationResponse>>> findNearby(
        @RequestParam BigDecimal lat,
        @RequestParam BigDecimal lng,
        @RequestParam(defaultValue = "10.0") double radiusKm
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.findNearby(lat, lng, radiusKm)));
    }

    @GetMapping("/nearby/available")
    @Operation(summary = "Find available stations near coordinates — used by Route Optimizer")
    public ResponseEntity<ApiResponse<List<StationResponse>>> findAvailableNearby(
        @RequestParam BigDecimal lat,
        @RequestParam BigDecimal lng,
        @RequestParam(defaultValue = "20.0") double radiusKm
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.findAvailableNearby(lat, lng, radiusKm)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update station details")
    public ResponseEntity<ApiResponse<StationResponse>> update(
        @PathVariable UUID id,
        @Valid @RequestBody StationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update station operational status")
    public ResponseEntity<ApiResponse<StationResponse>> updateStatus(
        @PathVariable UUID id,
        @RequestParam StationStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.updateStatus(id, status)));
    }

    @PostMapping("/{id}/connectors/occupy")
    @Operation(summary = "Mark one connector as occupied (vehicle session started)")
    public ResponseEntity<ApiResponse<StationResponse>> occupyConnector(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.occupyConnector(id)));
    }

    @PostMapping("/{id}/connectors/release")
    @Operation(summary = "Release one connector (vehicle session ended)")
    public ResponseEntity<ApiResponse<StationResponse>> releaseConnector(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(stationService.releaseConnector(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a station from the network")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        stationService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Station removed"));
    }
}

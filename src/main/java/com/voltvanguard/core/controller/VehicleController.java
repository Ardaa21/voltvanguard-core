package com.voltvanguard.core.controller;

import com.voltvanguard.core.dto.request.TelemetryUpdateRequest;
import com.voltvanguard.core.dto.request.VehicleRequest;
import com.voltvanguard.core.dto.response.ApiResponse;
import com.voltvanguard.core.dto.response.VehicleResponse;
import com.voltvanguard.core.enums.VehicleStatus;
import com.voltvanguard.core.service.VehicleService;
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
import java.util.Map;
import java.util.UUID;

/**
 * RESTful API for Electric Vehicle management.
 * Base path: /api/v1/vehicles
 */
@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Electric Vehicle fleet management")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @Operation(summary = "Register a new EV in the fleet")
    public ResponseEntity<ApiResponse<VehicleResponse>> register(
        @Valid @RequestBody VehicleRequest request
    ) {
        VehicleResponse response = vehicleService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Vehicle registered successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get EV by UUID")
    public ResponseEntity<ApiResponse<VehicleResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.findById(id)));
    }

    @GetMapping("/vin/{vin}")
    @Operation(summary = "Get EV by VIN")
    public ResponseEntity<ApiResponse<VehicleResponse>> findByVin(@PathVariable String vin) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.findByVin(vin)));
    }

    @GetMapping
    @Operation(summary = "List all EVs (paginated)")
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> findAll(
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.findAll(pageable)));
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(summary = "List EVs by owner")
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> findByOwner(
        @PathVariable UUID ownerId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.findByOwner(ownerId, pageable)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List EVs filtered by status")
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> findByStatus(
        @PathVariable VehicleStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.findByStatus(status, pageable)));
    }

    @PatchMapping("/{id}/telemetry")
    @Operation(summary = "Push live telemetry update (battery, location)")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateTelemetry(
        @PathVariable UUID id,
        @Valid @RequestBody TelemetryUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.updateTelemetry(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update vehicle status")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateStatus(
        @PathVariable UUID id,
        @RequestParam VehicleStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.updateStatus(id, status)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update vehicle details")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(
        @PathVariable UUID id,
        @Valid @RequestBody VehicleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deregister a vehicle from the fleet")
    public ResponseEntity<ApiResponse<Void>> deregister(@PathVariable UUID id) {
        vehicleService.deregister(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Vehicle deregistered"));
    }

    @GetMapping("/alerts/critical")
    @Operation(summary = "Get vehicles with critically low battery")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getCritical(
        @RequestParam(required = false) BigDecimal threshold
    ) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.findCriticalVehicles(threshold)));
    }

    @GetMapping("/analytics/fleet-summary")
    @Operation(summary = "Fleet status distribution for analytics dashboard")
    public ResponseEntity<ApiResponse<Map<VehicleStatus, Long>>> getFleetSummary() {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getFleetStatusSummary()));
    }
}

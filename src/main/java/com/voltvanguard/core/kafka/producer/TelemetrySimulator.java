package com.voltvanguard.core.kafka.producer;

import com.voltvanguard.core.entity.ElectricVehicle;
import com.voltvanguard.core.enums.VehicleStatus;
import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import com.voltvanguard.core.repository.ElectricVehicleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates EV telemetry streams for development and load testing.
 *
 * <p>Only active when {@code voltvanguard.simulator.enabled=true} (default: true in dev).
 * Disable in production via environment variable {@code SIMULATOR_ENABLED=false}.</p>
 *
 * <p>Simulation model:
 * <ul>
 *   <li>Each heartbeat drains battery by ~0.01–0.05% (realistic idle drain).</li>
 *   <li>Vehicle location drifts by ±0.0005° per tick (~50m displacement).</li>
 *   <li>Speed is randomly assigned 0–120 km/h for IN_TRANSIT vehicles.</li>
 *   <li>Battery temperature oscillates around 25°C with ±2°C noise.</li>
 *   <li>Sequence numbers are monotonically increasing per vehicle for gap detection.</li>
 * </ul>
 *
 * <p>The simulator caches the last known state of each vehicle in memory
 * ({@code simulatedStates}) to produce realistic incremental changes rather than
 * random, disconnected values on each tick.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "voltvanguard.simulator.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetrySimulator {

    private final ElectricVehicleRepository vehicleRepository;
    private final TelemetryProducer         telemetryProducer;

    @Value("${voltvanguard.simulator.max-vehicles:20}")
    private int maxVehicles;

    @Value("${voltvanguard.simulator.interval-ms:1000}")
    private long intervalMs;

    // ── In-memory per-vehicle simulated state ─────────────────────────────────

    /**
     * Holds the mutable simulation state for each active vehicle.
     * Thread-safe: map is ConcurrentHashMap; mutations are per-key only.
     */
    private final Map<UUID, VehicleSimState> simulatedStates = new ConcurrentHashMap<>();

    private final Random random = new Random();
    private final AtomicLong globalSequence = new AtomicLong(0);

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        log.info("TelemetrySimulator active — will simulate up to {} vehicles every {}ms",
            maxVehicles, intervalMs);
    }

    // ── Scheduled Tick ────────────────────────────────────────────────────────

    /**
     * Fires every {@code voltvanguard.simulator.interval-ms} milliseconds.
     * Fetches active vehicles, updates their simulated state, and publishes to Kafka.
     *
     * <p>Uses a fixed-delay (not fixed-rate) to prevent task pile-up if a tick
     * takes longer than the interval.</p>
     */
    @Scheduled(fixedDelayString = "${voltvanguard.simulator.interval-ms:1000}")
    public void tick() {
        // Simulate all active vehicle statuses — ONLINE, IN_TRANSIT, IDLE, CHARGING, BATTERY_CRITICAL
        List<VehicleStatus> activeStatuses = java.util.List.of(
            VehicleStatus.ONLINE, VehicleStatus.IN_TRANSIT,
            VehicleStatus.IDLE,   VehicleStatus.CHARGING,
            VehicleStatus.BATTERY_CRITICAL, VehicleStatus.AWAITING_TASK
        );
        List<ElectricVehicle> vehicles = vehicleRepository
            .findByStatusIn(activeStatuses, PageRequest.of(0, maxVehicles))
            .getContent();

        if (vehicles.isEmpty()) {
            log.trace("No active vehicles to simulate");
            return;
        }

        log.debug("Simulating telemetry for {} vehicles", vehicles.size());
        vehicles.forEach(this::simulateAndPublish);
    }

    // ── Core Simulation Logic ─────────────────────────────────────────────────

    private void simulateAndPublish(ElectricVehicle vehicle) {
        VehicleSimState state = simulatedStates.computeIfAbsent(
            vehicle.getId(), id -> VehicleSimState.from(vehicle)
        );

        // Battery: drain for IN_TRANSIT, charge for CHARGING, idle drain otherwise
        state.battery = switch (vehicle.getStatus()) {
            case IN_TRANSIT -> drain(state.battery, 0.02, 0.08);
            case CHARGING   -> charge(state.battery, 0.5, 1.5);
            default          -> drain(state.battery, 0.005, 0.02);
        };

        // Location drift (simulates movement)
        if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT) {
            state.latitude  = drift(state.latitude,  0.0005);
            state.longitude = drift(state.longitude, 0.0005);
        }

        // Speed
        state.speedKmh = switch (vehicle.getStatus()) {
            case IN_TRANSIT -> BigDecimal.valueOf(20 + random.nextInt(100));
            case CHARGING   -> BigDecimal.ZERO;
            default          -> BigDecimal.valueOf(random.nextInt(10));
        };

        // Battery temperature: oscillate around 25°C
        state.batteryTempC = BigDecimal.valueOf(25.0 + (random.nextDouble() * 4 - 2))
            .setScale(1, RoundingMode.HALF_UP);

        // Odometer: increment by speed/3600 (km per tick at 1s interval)
        state.odometerKm = state.odometerKm.add(
            state.speedKmh.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP)
        );

        // Estimated range: simple approximation (battery% × capacity / consumption_rate)
        BigDecimal estimatedRange = state.battery
            .multiply(vehicle.getBatteryCapacityKwh())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(6.0));  // ~6 km/kWh average efficiency

        VehicleTelemetryEvent event = VehicleTelemetryEvent.builder()
            .vehicleId(vehicle.getId())
            .vin(vehicle.getVin())
            .batteryPercent(state.battery)
            .estimatedRangeKm(estimatedRange)
            .speedKmh(state.speedKmh)
            .latitude(state.latitude)
            .longitude(state.longitude)
            .batteryTemperatureCelsius(state.batteryTempC)
            .odometerKm(state.odometerKm)
            .status(vehicle.getStatus().name())
            .sourceType(VehicleTelemetryEvent.TelemetrySource.SIMULATED)
            .capturedAt(Instant.now())
            .publishedAt(Instant.now())
            .sequenceNumber(state.sequence.getAndIncrement())
            .gatewayVersion("sim-1.0.0")
            .build();

        telemetryProducer.sendTelemetry(event);
    }

    // ── Math Helpers ──────────────────────────────────────────────────────────

    private BigDecimal drain(BigDecimal current, double minRate, double maxRate) {
        double drainRate = minRate + random.nextDouble() * (maxRate - minRate);
        BigDecimal result = current.subtract(BigDecimal.valueOf(drainRate))
            .setScale(2, RoundingMode.HALF_UP);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    private BigDecimal charge(BigDecimal current, double minRate, double maxRate) {
        double chargeRate = minRate + random.nextDouble() * (maxRate - minRate);
        BigDecimal result = current.add(BigDecimal.valueOf(chargeRate))
            .setScale(2, RoundingMode.HALF_UP);
        return result.compareTo(new BigDecimal("100.00")) > 0 ? new BigDecimal("100.00") : result;
    }

    private BigDecimal drift(BigDecimal coord, double maxDelta) {
        double delta = (random.nextDouble() * 2 - 1) * maxDelta;
        return coord.add(BigDecimal.valueOf(delta)).setScale(6, RoundingMode.HALF_UP);
    }

    // ── Simulation State ──────────────────────────────────────────────────────

    /**
     * Per-vehicle mutable state that persists across simulation ticks.
     * Stored in memory — not persisted — so resets on service restart.
     */
    private static class VehicleSimState {
        BigDecimal battery;
        BigDecimal latitude;
        BigDecimal longitude;
        BigDecimal speedKmh;
        BigDecimal batteryTempC;
        BigDecimal odometerKm;
        final AtomicLong sequence = new AtomicLong(0);

        static VehicleSimState from(ElectricVehicle vehicle) {
            VehicleSimState s = new VehicleSimState();
            s.battery     = vehicle.getBatteryPercent()  != null
                ? vehicle.getBatteryPercent() : new BigDecimal("80.00");
            s.latitude    = vehicle.getLatitude()   != null
                ? vehicle.getLatitude()   : new BigDecimal("37.774900");
            s.longitude   = vehicle.getLongitude()  != null
                ? vehicle.getLongitude()  : new BigDecimal("-122.419400");
            s.speedKmh    = BigDecimal.ZERO;
            s.batteryTempC = new BigDecimal("25.0");
            s.odometerKm  = vehicle.getTotalDistanceKm() != null
                ? vehicle.getTotalDistanceKm() : BigDecimal.ZERO;
            return s;
        }
    }
}

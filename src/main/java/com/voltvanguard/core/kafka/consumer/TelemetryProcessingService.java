package com.voltvanguard.core.kafka.consumer;

import com.voltvanguard.core.dto.request.TelemetryUpdateRequest;
import com.voltvanguard.core.kafka.event.VehicleAlertEvent;
import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import com.voltvanguard.core.kafka.producer.TelemetryProducer;
import com.voltvanguard.core.service.VehicleService;
import com.voltvanguard.core.websocket.TelemetryWebSocketHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core telemetry processing logic sitting between the Kafka consumer and the persistence layer.
 *
 * <h3>Processing Pipeline (per message)</h3>
 * <pre>
 *  Kafka Message
 *      │
 *      ▼
 *  [1] Validate & sanitize
 *      │
 *      ▼
 *  [2] Write to Redis telemetry cache  ← always, sub-millisecond
 *      │
 *      ▼
 *  [3] Evaluate critical conditions
 *      ├─ Battery crossed critical threshold → persist to DB + publish alert
 *      ├─ Battery recovered → persist to DB + publish recovery alert
 *      └─ Periodic persistence every N messages → persist to DB
 *      │
 *      ▼
 *  [4] Track sequence gaps (telemetry health)
 * </pre>
 *
 * <h3>Redis vs DB Write Strategy</h3>
 * <p>At 1 event/second per vehicle, a 100-vehicle fleet produces 6,000 DB writes/min
 * if every message were persisted. This class reduces that to ~100–200 writes/min by
 * writing to Redis on every message and flushing to PostgreSQL only when meaningful
 * state changes occur.</p>
 */
@Slf4j
@Service
public class TelemetryProcessingService {

    private static final String REDIS_TELEMETRY_PREFIX = "telemetry:";

    private final VehicleService    vehicleService;
    private final TelemetryProducer telemetryProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TelemetryWebSocketHandler webSocketHandler;

    @Value("${voltvanguard.telemetry.battery-critical-threshold:15.0}")
    private double criticalThreshold;

    @Value("${voltvanguard.telemetry.db-persistence-interval:5}")
    private int dbPersistenceInterval;

    @Value("${voltvanguard.cache.telemetry-ttl-seconds:30}")
    private long telemetryTtlSeconds;

    @Value("${voltvanguard.telemetry.alert-cooldown-seconds:300}")
    private long alertCooldownSeconds;

    // ── In-memory state (ephemeral, resets on restart) ────────────────────────

    /** Message counter per vehicle — drives periodic DB persistence. */
    private final Map<UUID, AtomicLong> messageCounters = new ConcurrentHashMap<>();

    /** Vehicles currently in critical battery state. */
    private final Set<UUID> criticalVehicles = ConcurrentHashMap.newKeySet();

    /** Last time an alert was published per vehicle — for cooldown enforcement. */
    private final Map<UUID, Instant> lastAlertTime = new ConcurrentHashMap<>();

    /** Last sequence number seen per vehicle — for gap detection. */
    private final Map<UUID, Long> lastSequenceNumbers = new ConcurrentHashMap<>();

    // ── Metrics ───────────────────────────────────────────────────────────────

    private final Counter dbWriteCounter;
    private final Counter redisWriteCounter;
    private final Counter alertPublishedCounter;
    private final Counter sequenceGapCounter;

    public TelemetryProcessingService(
        VehicleService vehicleService,
        TelemetryProducer telemetryProducer,
        RedisTemplate<String, Object> redisTemplate,
        TelemetryWebSocketHandler webSocketHandler,
        MeterRegistry meterRegistry
    ) {
        this.vehicleService    = vehicleService;
        this.telemetryProducer = telemetryProducer;
        this.redisTemplate     = redisTemplate;
        this.webSocketHandler  = webSocketHandler;

        this.dbWriteCounter      = Counter.builder("telemetry.db.writes")
            .description("Number of telemetry events persisted to PostgreSQL")
            .register(meterRegistry);
        this.redisWriteCounter   = Counter.builder("telemetry.redis.writes")
            .description("Number of telemetry events written to Redis")
            .register(meterRegistry);
        this.alertPublishedCounter = Counter.builder("telemetry.alerts.published")
            .description("Number of vehicle alerts published to Kafka")
            .register(meterRegistry);
        this.sequenceGapCounter  = Counter.builder("telemetry.sequence.gaps")
            .description("Detected gaps in vehicle telemetry sequence numbers")
            .register(meterRegistry);

        // Live gauge: how many vehicles are currently in critical state
        Gauge.builder("telemetry.vehicles.critical", criticalVehicles, Set::size)
            .description("Vehicles currently in critical battery state")
            .register(meterRegistry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main processing entry point — called by {@link TelemetryConsumer} for each Kafka message.
     *
     * @param event the incoming telemetry event
     */
    public void process(VehicleTelemetryEvent event) {
        UUID vehicleId = event.getVehicleId();

        // ── Step 1: Update Redis (always, fast path) ──────────────────────────
        updateRedisCache(event);

        // ── Step 1b: Broadcast to connected WebSocket clients ─────────────────
        webSocketHandler.broadcastTelemetry(event);

        // ── Step 2: Increment message counter ─────────────────────────────────
        long msgCount = messageCounters
            .computeIfAbsent(vehicleId, id -> new AtomicLong(0))
            .incrementAndGet();

        // ── Step 3: Critical battery state transitions ────────────────────────
        BigDecimal threshold = BigDecimal.valueOf(criticalThreshold);
        boolean isCritical  = event.isBatteryCritical(threshold);
        boolean wasCritical = criticalVehicles.contains(vehicleId);

        if (isCritical && !wasCritical) {
            // Newly critical — persist + alert
            criticalVehicles.add(vehicleId);
            persistToDatabase(event);
            publishAlertIfCooldownElapsed(VehicleAlertEvent.batteryCritical(event));
            log.warn("BATTERY CRITICAL: vehicle={} vin={} battery={}%",
                vehicleId, event.getVin(), event.getBatteryPercent());

        } else if (!isCritical && wasCritical) {
            // Recovered from critical
            criticalVehicles.remove(vehicleId);
            persistToDatabase(event);
            publishAlertIfCooldownElapsed(VehicleAlertEvent.batteryRecovered(event));
            log.info("Battery recovered: vehicle={} battery={}%", vehicleId, event.getBatteryPercent());

        } else if (msgCount % dbPersistenceInterval == 0) {
            // ── Step 4: Periodic DB flush (every N messages per vehicle) ──────
            persistToDatabase(event);
            log.debug("Periodic persistence: vehicle={} count={}", vehicleId, msgCount);
        }

        // ── Step 5: Sequence gap detection ────────────────────────────────────
        detectSequenceGap(vehicleId, event.getSequenceNumber());
    }

    // ── Redis ─────────────────────────────────────────────────────────────────

    /**
     * Stores the full telemetry event in Redis under key {@code telemetry:{vehicleId}}.
     * TTL is short (30s by default) — stale data is automatically evicted.
     *
     * <p>This key is separate from the vehicle entity cache ({@code vehicles:{id}})
     * so that high-frequency telemetry writes don't thrash the entity cache.</p>
     */
    private void updateRedisCache(VehicleTelemetryEvent event) {
        String key = REDIS_TELEMETRY_PREFIX + event.getVehicleId();
        try {
            redisTemplate.opsForValue().set(key, event, Duration.ofSeconds(telemetryTtlSeconds));
            redisWriteCounter.increment();
        } catch (Exception ex) {
            // Redis failure must never block Kafka processing
            log.warn("Redis write failed for vehicle={}: {}", event.getVehicleId(), ex.getMessage());
        }
    }

    // ── Database ──────────────────────────────────────────────────────────────

    /**
     * Persists telemetry to PostgreSQL via the VehicleService.
     * Catches exceptions so a DB outage doesn't break Kafka offset commits.
     */
    private void persistToDatabase(VehicleTelemetryEvent event) {
        try {
            TelemetryUpdateRequest request = new TelemetryUpdateRequest(
                event.getBatteryPercent(),
                event.getLatitude(),
                event.getLongitude(),
                event.getEstimatedRangeKm()
            );
            vehicleService.updateTelemetry(event.getVehicleId(), request);
            dbWriteCounter.increment();
        } catch (Exception ex) {
            log.error("DB persistence failed for vehicle={}: {}",
                event.getVehicleId(), ex.getMessage(), ex);
        }
    }

    // ── Alert Publishing ──────────────────────────────────────────────────────

    /**
     * Publishes an alert only if the cooldown period has elapsed since the last alert
     * for this vehicle. Prevents alert storms when a vehicle oscillates around the threshold.
     */
    private void publishAlertIfCooldownElapsed(VehicleAlertEvent alert) {
        UUID vehicleId = alert.getVehicleId();
        Instant lastAlert = lastAlertTime.get(vehicleId);
        Instant now = Instant.now();

        if (lastAlert == null || Duration.between(lastAlert, now).getSeconds() >= alertCooldownSeconds) {
            lastAlertTime.put(vehicleId, now);
            telemetryProducer.sendAlert(alert);
            alertPublishedCounter.increment();
        } else {
            log.debug("Alert suppressed (cooldown): vehicle={} type={}", vehicleId, alert.getAlertType());
        }
    }

    // ── Sequence Gap Detection ────────────────────────────────────────────────

    /**
     * Detects gaps in the per-vehicle monotonic sequence number.
     * A gap suggests missed heartbeats — could indicate connectivity issues.
     */
    private void detectSequenceGap(UUID vehicleId, long currentSeq) {
        Long lastSeq = lastSequenceNumbers.put(vehicleId, currentSeq);
        if (lastSeq != null && currentSeq > lastSeq + 1) {
            long missed = currentSeq - lastSeq - 1;
            sequenceGapCounter.increment(missed);
            log.warn("Telemetry gap detected: vehicle={} missed={} last={} current={}",
                vehicleId, missed, lastSeq, currentSeq);
        }
    }

    // ── Observable State (for health endpoints) ───────────────────────────────

    public int getCriticalVehicleCount() {
        return criticalVehicles.size();
    }

    public Set<UUID> getCriticalVehicleIds() {
        return Set.copyOf(criticalVehicles);
    }
}

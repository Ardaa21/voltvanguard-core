package com.voltvanguard.core.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka message payload representing a single EV telemetry heartbeat.
 *
 * <p>Produced by:
 * <ul>
 *   <li>IoT Gateway (real vehicles via MQTT)</li>
 *   <li>TelemetrySimulator (synthetic data for dev/testing)</li>
 * </ul>
 *
 * <p>Consumed by: {@code TelemetryConsumer} on topic {@code telemetry.raw}</p>
 *
 * <p>Design notes:
 * <ul>
 *   <li>Uses Lombok {@code @Data} (not record) for Jackson compatibility with
 *       Spring Kafka's {@code JsonDeserializer} — records require extra config.</li>
 *   <li>All numeric fields use {@code BigDecimal} to match entity precision.</li>
 *   <li>{@code sourceType} lets consumers distinguish real vs simulated data.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleTelemetryEvent implements Serializable {

    // ── Identity ─────────────────────────────────────────────────────────────

    /** UUID of the vehicle that produced this reading. */
    private UUID vehicleId;

    /** VIN — denormalized here to avoid a DB lookup on every Kafka message. */
    private String vin;

    // ── Telemetry Payload ────────────────────────────────────────────────────

    /** Battery state-of-charge (0.00 – 100.00 %). */
    private BigDecimal batteryPercent;

    /** Estimated remaining range in kilometres at current consumption rate. */
    private BigDecimal estimatedRangeKm;

    /** Instantaneous speed in km/h. */
    private BigDecimal speedKmh;

    /** Current latitude (WGS-84). */
    private BigDecimal latitude;

    /** Current longitude (WGS-84). */
    private BigDecimal longitude;

    /** Battery pack temperature in °C. Null if sensor unavailable. */
    private BigDecimal batteryTemperatureCelsius;

    /** Cumulative odometer reading in km. */
    private BigDecimal odometerKm;

    // ── Derived / Contextual ─────────────────────────────────────────────────

    /** Current vehicle status as a string (avoids enum dependency on the event). */
    private String status;

    /**
     * Source of this telemetry event.
     * {@code REAL} = physical IoT device, {@code SIMULATED} = TelemetrySimulator.
     */
    @Builder.Default
    private TelemetrySource sourceType = TelemetrySource.REAL;

    /** Firmware / gateway version — useful for diagnosing stale sensor data. */
    private String gatewayVersion;

    // ── Timing ───────────────────────────────────────────────────────────────

    /** Wall-clock time the event was captured on the vehicle. */
    private Instant capturedAt;

    /** Wall-clock time the event was published to Kafka by the producer. */
    @Builder.Default
    private Instant publishedAt = Instant.now();

    /** Monotonically increasing sequence number per vehicle — enables gap detection. */
    private long sequenceNumber;

    // ── Nested Types ─────────────────────────────────────────────────────────

    public enum TelemetrySource {
        REAL, SIMULATED
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isBatteryCritical(BigDecimal threshold) {
        return batteryPercent != null && batteryPercent.compareTo(threshold) <= 0;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}

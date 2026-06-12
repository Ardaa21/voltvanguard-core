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
 * Kafka alert event published to {@code vehicle.alerts} when a critical condition
 * is detected during telemetry processing.
 *
 * <p>Downstream consumers (e.g. NotificationService, GridOrchestrator) subscribe
 * to this topic to trigger push notifications or autonomous charging decisions.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleAlertEvent implements Serializable {

    private UUID alertId;
    private UUID vehicleId;
    private String vin;

    private AlertType alertType;
    private AlertSeverity severity;

    /** Human-readable description of the alert condition. */
    private String message;

    /** Battery level at the time the alert was triggered. */
    private BigDecimal batteryPercent;

    /** Vehicle location at alert time — for directing to nearest charging station. */
    private BigDecimal latitude;
    private BigDecimal longitude;

    /** Reference to the telemetry event that triggered this alert. */
    private long triggerSequenceNumber;

    @Builder.Default
    private Instant occurredAt = Instant.now();

    // ── Nested Types ──────────────────────────────────────────────────────────

    public enum AlertType {
        BATTERY_CRITICAL,        // Battery below critical threshold
        BATTERY_RECOVERED,       // Battery rose above critical threshold (charging)
        VEHICLE_OFFLINE,         // No telemetry received within expected window
        BATTERY_TEMP_HIGH,       // Battery temperature exceeds safe operating range
        TELEMETRY_GAP_DETECTED   // Sequence number gap detected (missed heartbeats)
    }

    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }

    // ── Factory Methods ───────────────────────────────────────────────────────

    public static VehicleAlertEvent batteryCritical(VehicleTelemetryEvent telemetry) {
        BigDecimal battery = telemetry.getBatteryPercent() != null
            ? telemetry.getBatteryPercent() : BigDecimal.ZERO;
        return VehicleAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .vehicleId(telemetry.getVehicleId())
            .vin(telemetry.getVin())
            .alertType(AlertType.BATTERY_CRITICAL)
            .severity(AlertSeverity.CRITICAL)
            .message("Battery critically low: %.1f%% — immediate charging required"
                .formatted(battery))
            .batteryPercent(battery)
            .latitude(telemetry.getLatitude())
            .longitude(telemetry.getLongitude())
            .triggerSequenceNumber(telemetry.getSequenceNumber())
            .build();
    }

    public static VehicleAlertEvent batteryRecovered(VehicleTelemetryEvent telemetry) {
        BigDecimal battery = telemetry.getBatteryPercent() != null
            ? telemetry.getBatteryPercent() : BigDecimal.ZERO;
        return VehicleAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .vehicleId(telemetry.getVehicleId())
            .vin(telemetry.getVin())
            .alertType(AlertType.BATTERY_RECOVERED)
            .severity(AlertSeverity.INFO)
            .message("Battery recovered: %.1f%% — vehicle back in normal range"
                .formatted(battery))
            .batteryPercent(battery)
            .latitude(telemetry.getLatitude())
            .longitude(telemetry.getLongitude())
            .triggerSequenceNumber(telemetry.getSequenceNumber())
            .build();
    }
}

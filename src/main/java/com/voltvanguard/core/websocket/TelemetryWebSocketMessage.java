package com.voltvanguard.core.websocket;

import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WebSocket payload sent to Flutter clients for each telemetry event.
 *
 * <p>Field mapping from {@link VehicleTelemetryEvent}:
 * <ul>
 *   <li>{@code capturedAt} → {@code timestamp}  (Flutter expects "timestamp")</li>
 *   <li>All numeric fields kept as-is</li>
 *   <li>{@code status} serialized as uppercase string, e.g. "ONLINE", "BATTERY_CRITICAL"</li>
 * </ul>
 */
@Value
@Builder
public class TelemetryWebSocketMessage {

    /** Message type discriminator — Flutter uses this to route payloads. */
    String type;

    /** Vehicle UUID as string. */
    String vehicleId;

    /** Vehicle VIN. */
    String vin;

    /** Battery state-of-charge, 0–100. Nullable when sensor data is unavailable. */
    BigDecimal batteryPercent;

    /** Estimated remaining range in kilometres. */
    BigDecimal estimatedRangeKm;

    /** Current speed in km/h. */
    BigDecimal speedKmh;

    /** GPS latitude. */
    BigDecimal latitude;

    /** GPS longitude. */
    BigDecimal longitude;

    /** Battery temperature in Celsius. */
    BigDecimal batteryTemperatureCelsius;

    /** Vehicle status string, e.g. "ONLINE", "CHARGING", "BATTERY_CRITICAL". */
    String status;

    /**
     * ISO-8601 timestamp when telemetry was captured on the vehicle.
     * Named "timestamp" (not "capturedAt") because Flutter's VehicleTelemetryModel
     * expects a field called "timestamp".
     */
    Instant timestamp;

    /** How this reading was produced — "REAL" or "SIMULATED". */
    String sourceType;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static TelemetryWebSocketMessage from(VehicleTelemetryEvent event) {
        return TelemetryWebSocketMessage.builder()
            .type("TELEMETRY_UPDATE")
            .vehicleId(event.getVehicleId() != null ? event.getVehicleId().toString() : null)
            .vin(event.getVin())
            .batteryPercent(event.getBatteryPercent())
            .estimatedRangeKm(event.getEstimatedRangeKm())
            .speedKmh(event.getSpeedKmh())
            .latitude(event.getLatitude())
            .longitude(event.getLongitude())
            .batteryTemperatureCelsius(event.getBatteryTemperatureCelsius())
            .status(event.getStatus())   // already a String (e.g. "ONLINE", "BATTERY_CRITICAL")
            .timestamp(event.getCapturedAt() != null ? event.getCapturedAt() : event.getPublishedAt())
            .sourceType(event.getSourceType() != null ? event.getSourceType().name() : null)
            .build();
    }
}

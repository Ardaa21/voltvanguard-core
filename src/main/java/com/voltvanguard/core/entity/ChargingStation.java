package com.voltvanguard.core.entity;

import com.voltvanguard.core.enums.StationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a physical EV charging station in the VoltVanguard grid network.
 *
 * <p>Connector availability is updated in real time by the Charging Service
 * as vehicles connect and disconnect via OCPP events from Kafka.</p>
 */
@Entity
@Table(
    name = "charging_stations",
    indexes = {
        @Index(name = "idx_station_status",   columnList = "status"),
        @Index(name = "idx_station_location", columnList = "latitude, longitude")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ChargingStation extends BaseEntity {

    // ── Identity ────────────────────────────────────────────────────────────

    @EqualsAndHashCode.Include
    @NotBlank
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Human-readable street address for UI display. */
    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    // ── Location ────────────────────────────────────────────────────────────

    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    // ── Capacity ─────────────────────────────────────────────────────────────

    @NotNull
    @Min(1) @Max(500)
    @Column(name = "total_connectors", nullable = false)
    private Integer totalConnectors;

    @NotNull
    @Min(0)
    @Column(name = "available_connectors", nullable = false)
    @Builder.Default
    private Integer availableConnectors = 0;

    /**
     * Maximum output power in kilowatts.
     * Example: 50 kW DC fast charger, 350 kW ultra-fast.
     */
    @NotNull
    @DecimalMin("1.0") @DecimalMax("500.0")
    @Column(name = "power_kw", nullable = false, precision = 6, scale = 2)
    private BigDecimal powerKw;

    // ── Pricing ──────────────────────────────────────────────────────────────

    /** Price per kWh in USD cents (integer to avoid floating-point issues). */
    @Min(0)
    @Column(name = "price_per_kwh_cents")
    @Builder.Default
    private Integer pricePerKwhCents = 0;

    // ── Status ───────────────────────────────────────────────────────────────

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private StationStatus status = StationStatus.OFFLINE;

    /** Optional network operator (e.g., "Tesla Supercharger", "ChargePoint"). */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    // ── Domain Helpers ────────────────────────────────────────────────────────

    public boolean hasAvailableConnector() {
        return StationStatus.AVAILABLE.equals(status) && availableConnectors > 0;
    }

    public double getOccupancyRate() {
        if (totalConnectors == 0) return 1.0;
        return (double) (totalConnectors - availableConnectors) / totalConnectors;
    }

    /**
     * Decrements available connectors and updates status to BUSY if full.
     * Called by ChargingService when a vehicle starts a session.
     */
    public void occupyConnector() {
        if (availableConnectors <= 0) {
            throw new IllegalStateException("No available connectors at station: " + name);
        }
        availableConnectors--;
        if (availableConnectors == 0) {
            status = StationStatus.BUSY;
        }
    }

    /**
     * Increments available connectors and updates status to AVAILABLE if needed.
     * Called by ChargingService when a vehicle ends a session.
     */
    public void releaseConnector() {
        if (availableConnectors >= totalConnectors) {
            throw new IllegalStateException("All connectors already available at station: " + name);
        }
        availableConnectors++;
        if (StationStatus.BUSY.equals(status)) {
            status = StationStatus.AVAILABLE;
        }
    }
}

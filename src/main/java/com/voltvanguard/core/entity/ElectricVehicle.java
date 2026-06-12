package com.voltvanguard.core.entity;

import com.voltvanguard.core.enums.VehicleStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an Electric Vehicle registered in the VoltVanguard fleet.
 *
 * <p>Telemetry fields (batteryPercent, latitude, longitude) are updated
 * continuously by the Telemetry Service via Kafka events. The {@code version}
 * field in BaseEntity provides optimistic locking to prevent stale writes.</p>
 */
@Entity
@Table(
    name = "electric_vehicles",
    indexes = {
        @Index(name = "idx_ev_vin",    columnList = "vin",        unique = true),
        @Index(name = "idx_ev_owner",  columnList = "owner_id"),
        @Index(name = "idx_ev_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "tasks")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ElectricVehicle extends BaseEntity {

    // ── Identity ────────────────────────────────────────────────────────────

    /**
     * Vehicle Identification Number — globally unique, 17-character ISO standard.
     */
    @EqualsAndHashCode.Include
    @NotBlank
    @Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")
    @Column(name = "vin", nullable = false, unique = true, length = 17)
    private String vin;

    @NotBlank
    @Column(name = "manufacturer", nullable = false, length = 100)
    private String manufacturer;

    @NotBlank
    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @NotNull
    @Min(2000) @Max(2100)
    @Column(name = "model_year", nullable = false)
    private Integer modelYear;

    // ── Ownership ───────────────────────────────────────────────────────────

    /** References the User entity (managed by user-service). Stored as plain UUID. */
    @NotNull
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    // ── Battery ─────────────────────────────────────────────────────────────

    @NotNull
    @DecimalMin("1.0") @DecimalMax("250.0")
    @Column(name = "battery_capacity_kwh", nullable = false, precision = 6, scale = 2)
    private BigDecimal batteryCapacityKwh;

    /**
     * Last known battery state-of-charge as a percentage (0.00 – 100.00).
     * Updated via telemetry stream; never null after first heartbeat.
     */
    @DecimalMin("0.00") @DecimalMax("100.00")
    @Column(name = "battery_percent", precision = 5, scale = 2)
    private BigDecimal batteryPercent;

    @Column(name = "battery_last_updated_at")
    private Instant batteryLastUpdatedAt;

    // ── Location ────────────────────────────────────────────────────────────

    @DecimalMin("-90.0") @DecimalMax("90.0")
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "location_last_updated_at")
    private Instant locationLastUpdatedAt;

    // ── Status ──────────────────────────────────────────────────────────────

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.OFFLINE;

    // ── Range & Performance ─────────────────────────────────────────────────

    /** Estimated remaining range in kilometres, computed by AI Charge Predictor. */
    @Column(name = "estimated_range_km", precision = 7, scale = 2)
    private BigDecimal estimatedRangeKm;

    @Column(name = "total_distance_km", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalDistanceKm = BigDecimal.ZERO;

    // ── Relationships ───────────────────────────────────────────────────────

    @OneToMany(
        mappedBy    = "vehicle",
        cascade     = CascadeType.ALL,
        fetch       = FetchType.LAZY,
        orphanRemoval = true
    )
    @Builder.Default
    private List<AutonomousTask> tasks = new ArrayList<>();

    // ── Domain Helpers ───────────────────────────────────────────────────────

    /** Convenience method: true if battery is below the critical threshold. */
    public boolean isBatteryCritical() {
        return batteryPercent != null
            && batteryPercent.compareTo(new BigDecimal("15.00")) <= 0;
    }

    /** Convenience method: true if vehicle is reachable by telemetry gateway. */
    public boolean isOnline() {
        return status != VehicleStatus.OFFLINE;
    }
}

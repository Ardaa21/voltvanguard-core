package com.voltvanguard.core.repository;

import com.voltvanguard.core.entity.ElectricVehicle;
import com.voltvanguard.core.enums.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link ElectricVehicle}.
 * All heavy query logic lives here; services remain orchestration-only.
 */
@Repository
public interface ElectricVehicleRepository extends JpaRepository<ElectricVehicle, UUID> {

    Optional<ElectricVehicle> findByVin(String vin);

    boolean existsByVin(String vin);

    Page<ElectricVehicle> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<ElectricVehicle> findByStatus(VehicleStatus status, Pageable pageable);

    Page<ElectricVehicle> findByStatusIn(java.util.Collection<VehicleStatus> statuses, Pageable pageable);

    List<ElectricVehicle> findByOwnerIdAndStatus(UUID ownerId, VehicleStatus status);

    /** Vehicles that need immediate charging attention. */
    @Query("""
        SELECT v FROM ElectricVehicle v
        WHERE v.batteryPercent <= :threshold
          AND v.status != 'CHARGING'
          AND v.status != 'OFFLINE'
        ORDER BY v.batteryPercent ASC
        """)
    List<ElectricVehicle> findVehiclesBelowBatteryThreshold(@Param("threshold") BigDecimal threshold);

    /** Vehicles within a bounding box — used by AI route optimizer. */
    @Query("""
        SELECT v FROM ElectricVehicle v
        WHERE v.latitude  BETWEEN :minLat AND :maxLat
          AND v.longitude BETWEEN :minLng AND :maxLng
          AND v.status != 'OFFLINE'
        """)
    List<ElectricVehicle> findVehiclesInBoundingBox(
        @Param("minLat") BigDecimal minLat,
        @Param("maxLat") BigDecimal maxLat,
        @Param("minLng") BigDecimal minLng,
        @Param("maxLng") BigDecimal maxLng
    );

    /** Bulk status update — used by Telemetry Service for batch heartbeat processing. */
    @Modifying
    @Transactional
    @Query("UPDATE ElectricVehicle v SET v.status = :status WHERE v.id IN :ids")
    int bulkUpdateStatus(@Param("ids") List<UUID> ids, @Param("status") VehicleStatus status);

    /** Count vehicles grouped by status — used for fleet analytics dashboard. */
    @Query("SELECT v.status, COUNT(v) FROM ElectricVehicle v GROUP BY v.status")
    List<Object[]> countByStatus();
}

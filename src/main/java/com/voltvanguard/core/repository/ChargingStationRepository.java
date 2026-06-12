package com.voltvanguard.core.repository;

import com.voltvanguard.core.entity.ChargingStation;
import com.voltvanguard.core.enums.StationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Data access layer for {@link ChargingStation}.
 * Geospatial proximity queries use a Haversine approximation in JPQL;
 * for production-scale geo search, consider PostGIS + native queries.
 */
@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, UUID> {

    Page<ChargingStation> findByStatus(StationStatus status, Pageable pageable);

    List<ChargingStation> findByCity(String city);

    List<ChargingStation> findByOperatorName(String operatorName);

    /** All stations that currently have at least one free connector. */
    @Query("""
        SELECT s FROM ChargingStation s
        WHERE s.status = 'AVAILABLE'
          AND s.availableConnectors > 0
        ORDER BY s.availableConnectors DESC
        """)
    List<ChargingStation> findAllAvailable();

    /**
     * Stations within radius using Haversine formula (kilometres).
     * The constant 6371 is Earth's mean radius in km.
     */
    @Query("""
        SELECT s FROM ChargingStation s
        WHERE (6371 * acos(
                cos(radians(:lat)) * cos(radians(s.latitude))
                * cos(radians(s.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(s.latitude))
              )) <= :radiusKm
          AND s.status != 'OFFLINE'
          AND s.status != 'MAINTENANCE'
        ORDER BY (6371 * acos(
                cos(radians(:lat)) * cos(radians(s.latitude))
                * cos(radians(s.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(s.latitude))
              )) ASC
        """)
    List<ChargingStation> findNearby(
        @Param("lat")      BigDecimal lat,
        @Param("lng")      BigDecimal lng,
        @Param("radiusKm") double radiusKm
    );

    /**
     * Available stations near a point — used by Route Optimizer agent
     * to select the best charging stop along a route.
     */
    @Query("""
        SELECT s FROM ChargingStation s
        WHERE s.status = 'AVAILABLE'
          AND s.availableConnectors > 0
          AND (6371 * acos(
                cos(radians(:lat)) * cos(radians(s.latitude))
                * cos(radians(s.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(s.latitude))
              )) <= :radiusKm
        ORDER BY s.powerKw DESC
        """)
    List<ChargingStation> findAvailableNearby(
        @Param("lat")      BigDecimal lat,
        @Param("lng")      BigDecimal lng,
        @Param("radiusKm") double radiusKm
    );

    /** Stations with power output at or above a minimum threshold. */
    @Query("SELECT s FROM ChargingStation s WHERE s.powerKw >= :minPowerKw AND s.status = 'AVAILABLE'")
    List<ChargingStation> findByMinPower(@Param("minPowerKw") BigDecimal minPowerKw);
}

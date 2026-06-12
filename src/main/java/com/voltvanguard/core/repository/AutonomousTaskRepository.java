package com.voltvanguard.core.repository;

import com.voltvanguard.core.entity.AutonomousTask;
import com.voltvanguard.core.enums.TaskStatus;
import com.voltvanguard.core.enums.TaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data access layer for {@link AutonomousTask}.
 */
@Repository
public interface AutonomousTaskRepository extends JpaRepository<AutonomousTask, UUID> {

    Page<AutonomousTask> findByVehicleId(UUID vehicleId, Pageable pageable);

    Page<AutonomousTask> findByStatus(TaskStatus status, Pageable pageable);

    List<AutonomousTask> findByVehicleIdAndStatus(UUID vehicleId, TaskStatus status);

    long countByVehicleIdAndStatusIn(UUID vehicleId, List<TaskStatus> statuses);

    /** Active tasks for a vehicle — used to enforce the concurrency cap. */
    @Query("""
        SELECT COUNT(t) FROM AutonomousTask t
        WHERE t.vehicle.id = :vehicleId
          AND t.status IN ('PENDING', 'IN_PROGRESS')
        """)
    long countActiveTasksForVehicle(@Param("vehicleId") UUID vehicleId);

    /**
     * Fetch next batch of PENDING tasks ordered by priority and schedule time.
     * Called by the AI agent dispatcher endpoint.
     */
    @Query("""
        SELECT t FROM AutonomousTask t
        WHERE t.status = 'PENDING'
          AND t.taskType = :taskType
          AND (t.scheduledAt IS NULL OR t.scheduledAt <= :now)
          AND (t.expiresAt IS NULL OR t.expiresAt > :now)
        ORDER BY t.priority ASC, t.scheduledAt ASC NULLS FIRST
        """)
    List<AutonomousTask> findNextPending(
        @Param("taskType") TaskType taskType,
        @Param("now")      Instant now,
        Pageable pageable
    );

    /** Transition all expired PENDING/IN_PROGRESS tasks to EXPIRED. */
    @Modifying
    @Transactional
    @Query("""
        UPDATE AutonomousTask t
        SET t.status = 'EXPIRED'
        WHERE t.expiresAt < :now
          AND t.status IN ('PENDING', 'IN_PROGRESS')
        """)
    int expireOverdueTasks(@Param("now") Instant now);

    /** Task history for a vehicle in a time window — for analytics. */
    @Query("""
        SELECT t FROM AutonomousTask t
        WHERE t.vehicle.id = :vehicleId
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
        """)
    List<AutonomousTask> findHistoryForVehicle(
        @Param("vehicleId") UUID vehicleId,
        @Param("from")      Instant from,
        @Param("to")        Instant to
    );
}

package com.voltvanguard.core.entity;

import com.voltvanguard.core.enums.TaskStatus;
import com.voltvanguard.core.enums.TaskType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Represents an autonomous decision task delegated to the Python AI agent layer.
 *
 * <p>{@code payload} stores the input context for the agent (e.g., target battery level,
 * destination coordinates) as PostgreSQL JSONB. {@code result} stores the agent's
 * output (e.g., recommended route, selected station) once the task completes.</p>
 *
 * <p>Tasks are created by Spring Boot services and consumed by Python agents via Kafka.
 * The agent updates the task status by calling back through the REST API.</p>
 */
@Entity
@Table(
    name = "autonomous_tasks",
    indexes = {
        @Index(name = "idx_task_vehicle",       columnList = "vehicle_id"),
        @Index(name = "idx_task_status",        columnList = "status"),
        @Index(name = "idx_task_type_status",   columnList = "task_type, status"),
        @Index(name = "idx_task_scheduled_at",  columnList = "scheduled_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"payload", "result"})
@EqualsAndHashCode(callSuper = false)
public class AutonomousTask extends BaseEntity {

    // ── Ownership ────────────────────────────────────────────────────────────

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_task_vehicle"))
    private ElectricVehicle vehicle;

    // ── Classification ────────────────────────────────────────────────────────

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40)
    private TaskType taskType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    // ── Priority ──────────────────────────────────────────────────────────────

    /**
     * Task priority: 1 (highest) – 10 (lowest).
     * AI agents pull tasks ordered by priority and scheduled_at.
     */
    @Min(1) @Max(10)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 5;

    // ── Scheduling ────────────────────────────────────────────────────────────

    /** Earliest point at which the AI agent should process this task. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Wall-clock deadline; tasks not started by this time transition to EXPIRED. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    // ── Agent I/O — stored as PostgreSQL JSONB ───────────────────────────────

    /**
     * Input context for the AI agent. JSON structure varies by {@link TaskType}.
     * Example for ROUTE_OPTIMIZATION:
     * <pre>{"destination": {"lat": 37.7749, "lng": -122.4194}, "minBatteryOnArrival": 20}</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    /**
     * Agent's output after processing. JSON structure varies by {@link TaskType}.
     * Example for ROUTE_OPTIMIZATION:
     * <pre>{"waypoints": [...], "estimatedDurationMin": 42, "chargingStopId": "uuid"}</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private String result;

    /** Human-readable error message if status is FAILED. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ── Agent Identity ────────────────────────────────────────────────────────

    /** ID of the Python agent instance that claimed this task (for debugging). */
    @Column(name = "agent_id", length = 100)
    private String agentId;

    // ── Domain Helpers ────────────────────────────────────────────────────────

    public boolean isTerminal() {
        return status == TaskStatus.COMPLETED
            || status == TaskStatus.FAILED
            || status == TaskStatus.CANCELLED
            || status == TaskStatus.EXPIRED;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt)
            && !isTerminal();
    }

    /** Marks the task as started by an AI agent. */
    public void markInProgress(String agentId) {
        this.status    = TaskStatus.IN_PROGRESS;
        this.agentId   = agentId;
        this.startedAt = Instant.now();
    }

    /** Marks the task as successfully completed with the agent result payload. */
    public void markCompleted(String result) {
        this.status      = TaskStatus.COMPLETED;
        this.result      = result;
        this.completedAt = Instant.now();
    }

    /** Marks the task as failed, recording the root cause. */
    public void markFailed(String errorMessage) {
        this.status       = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt  = Instant.now();
    }
}

package com.voltvanguard.core.dto.request;

import com.voltvanguard.core.enums.TaskType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for creating a new AutonomousTask.
 * The {@code payload} field is a free-form JSON string whose structure
 * is defined per-{@link TaskType} in the AI agent contracts (shared/openapi).
 */
public record TaskRequest(

    @NotNull(message = "Vehicle ID is required")
    UUID vehicleId,

    @NotNull(message = "Task type is required")
    TaskType taskType,

    @Min(1) @Max(10)
    Integer priority,

    /** ISO-8601 timestamp; if null the task is eligible for immediate dispatch. */
    Instant scheduledAt,

    /** ISO-8601 timestamp; if null the task never expires. */
    Instant expiresAt,

    /** JSON string conforming to the task-type-specific agent input schema. */
    @NotBlank(message = "Payload is required")
    String payload
) {}

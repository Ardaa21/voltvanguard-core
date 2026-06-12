package com.voltvanguard.core.dto.response;

import com.voltvanguard.core.entity.AutonomousTask;
import com.voltvanguard.core.enums.TaskStatus;
import com.voltvanguard.core.enums.TaskType;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of AutonomousTask returned to API consumers.
 */
public record TaskResponse(
    UUID       id,
    UUID       vehicleId,
    TaskType   taskType,
    TaskStatus status,
    Integer    priority,
    String     payload,
    String     result,
    String     errorMessage,
    String     agentId,
    boolean    isTerminal,
    Instant    scheduledAt,
    Instant    expiresAt,
    Instant    startedAt,
    Instant    completedAt,
    Instant    createdAt,
    Instant    updatedAt
) {
    public static TaskResponse from(AutonomousTask task) {
        return new TaskResponse(
            task.getId(),
            task.getVehicle().getId(),
            task.getTaskType(),
            task.getStatus(),
            task.getPriority(),
            task.getPayload(),
            task.getResult(),
            task.getErrorMessage(),
            task.getAgentId(),
            task.isTerminal(),
            task.getScheduledAt(),
            task.getExpiresAt(),
            task.getStartedAt(),
            task.getCompletedAt(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }
}

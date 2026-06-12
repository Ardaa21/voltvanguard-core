package com.voltvanguard.core.service;

import com.voltvanguard.core.dto.request.TaskRequest;
import com.voltvanguard.core.dto.request.TaskResultRequest;
import com.voltvanguard.core.dto.response.TaskResponse;
import com.voltvanguard.core.enums.TaskStatus;
import com.voltvanguard.core.enums.TaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Contract for all AutonomousTask business operations.
 */
public interface TaskService {

    TaskResponse create(TaskRequest request);

    TaskResponse findById(UUID id);

    Page<TaskResponse> findByVehicle(UUID vehicleId, Pageable pageable);

    Page<TaskResponse> findByStatus(TaskStatus status, Pageable pageable);

    /**
     * Claims and returns the next batch of PENDING tasks for a given type.
     * Called by the Python AI dispatcher agent.
     */
    List<TaskResponse> claimNextPending(TaskType taskType, int batchSize);

    /** Records an agent's result and transitions the task to COMPLETED or FAILED. */
    TaskResponse submitResult(UUID taskId, TaskResultRequest resultRequest);

    /** Cancels a task that has not yet reached a terminal state. */
    TaskResponse cancel(UUID taskId);

    /**
     * Scheduled sweep: transitions PENDING/IN_PROGRESS tasks past their
     * {@code expiresAt} deadline to EXPIRED.
     */
    int expireOverdueTasks();
}

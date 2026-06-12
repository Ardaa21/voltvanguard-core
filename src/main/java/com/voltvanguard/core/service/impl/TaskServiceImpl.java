package com.voltvanguard.core.service.impl;

import com.voltvanguard.core.dto.request.TaskRequest;
import com.voltvanguard.core.dto.request.TaskResultRequest;
import com.voltvanguard.core.dto.response.TaskResponse;
import com.voltvanguard.core.entity.AutonomousTask;
import com.voltvanguard.core.entity.ElectricVehicle;
import com.voltvanguard.core.enums.TaskStatus;
import com.voltvanguard.core.enums.TaskType;
import com.voltvanguard.core.exception.BusinessRuleException;
import com.voltvanguard.core.exception.ResourceNotFoundException;
import com.voltvanguard.core.repository.AutonomousTaskRepository;
import com.voltvanguard.core.repository.ElectricVehicleRepository;
import com.voltvanguard.core.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for AutonomousTask lifecycle management.
 *
 * <p>Task concurrency is enforced per-vehicle: no vehicle may have more than
 * {@code voltvanguard.task.max-concurrent-per-vehicle} active tasks at once.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskServiceImpl implements TaskService {

    @Value("${voltvanguard.task.max-concurrent-per-vehicle:3}")
    private int maxConcurrentTasksPerVehicle;

    private final AutonomousTaskRepository taskRepository;
    private final ElectricVehicleRepository vehicleRepository;

    // ── Write Operations ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request) {
        log.info("Creating task type={} for vehicle={}", request.taskType(), request.vehicleId());

        ElectricVehicle vehicle = vehicleRepository.findById(request.vehicleId())
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", request.vehicleId().toString()));

        long activeTasks = taskRepository.countActiveTasksForVehicle(request.vehicleId());
        if (activeTasks >= maxConcurrentTasksPerVehicle) {
            throw new BusinessRuleException(
                "Vehicle %s already has %d active tasks (max: %d)"
                    .formatted(request.vehicleId(), activeTasks, maxConcurrentTasksPerVehicle)
            );
        }

        AutonomousTask task = AutonomousTask.builder()
            .vehicle(vehicle)
            .taskType(request.taskType())
            .priority(request.priority() != null ? request.priority() : 5)
            .scheduledAt(request.scheduledAt())
            .expiresAt(request.expiresAt())
            .payload(request.payload())
            .status(TaskStatus.PENDING)
            .build();

        AutonomousTask saved = taskRepository.save(task);
        log.info("Task created: id={}, type={}", saved.getId(), saved.getTaskType());
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional
    public List<TaskResponse> claimNextPending(TaskType taskType, int batchSize) {
        log.debug("AI agent claiming up to {} tasks of type {}", batchSize, taskType);

        Pageable pageable = PageRequest.of(0, batchSize);
        List<AutonomousTask> tasks = taskRepository.findNextPending(taskType, Instant.now(), pageable);

        // Claim all retrieved tasks in a single transaction
        tasks.forEach(task -> task.setStatus(TaskStatus.IN_PROGRESS));
        taskRepository.saveAll(tasks);

        log.info("Claimed {} tasks of type {}", tasks.size(), taskType);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Override
    @Transactional
    public TaskResponse submitResult(UUID taskId, TaskResultRequest resultRequest) {
        AutonomousTask task = getOrThrow(taskId);

        if (task.isTerminal()) {
            throw new BusinessRuleException(
                "Task %s is already in terminal state: %s".formatted(taskId, task.getStatus())
            );
        }

        if (resultRequest.success()) {
            task.markCompleted(resultRequest.result());
            log.info("Task {} completed by agent {}", taskId, resultRequest.agentId());
        } else {
            task.markFailed(resultRequest.errorMessage());
            log.warn("Task {} failed: {}", taskId, resultRequest.errorMessage());
        }

        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    @Transactional
    public TaskResponse cancel(UUID taskId) {
        AutonomousTask task = getOrThrow(taskId);

        if (task.isTerminal()) {
            throw new BusinessRuleException(
                "Cannot cancel task %s in terminal state: %s".formatted(taskId, task.getStatus())
            );
        }

        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(Instant.now());
        log.info("Task {} cancelled", taskId);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${voltvanguard.task.expiry-check-ms:60000}")
    public int expireOverdueTasks() {
        int expired = taskRepository.expireOverdueTasks(Instant.now());
        if (expired > 0) {
            log.warn("Expired {} overdue tasks", expired);
        }
        return expired;
    }

    // ── Read Operations ───────────────────────────────────────────────────────

    @Override
    public TaskResponse findById(UUID id) {
        return TaskResponse.from(getOrThrow(id));
    }

    @Override
    public Page<TaskResponse> findByVehicle(UUID vehicleId, Pageable pageable) {
        return taskRepository.findByVehicleId(vehicleId, pageable).map(TaskResponse::from);
    }

    @Override
    public Page<TaskResponse> findByStatus(TaskStatus status, Pageable pageable) {
        return taskRepository.findByStatus(status, pageable).map(TaskResponse::from);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private AutonomousTask getOrThrow(UUID id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AutonomousTask", "id", id.toString()));
    }
}

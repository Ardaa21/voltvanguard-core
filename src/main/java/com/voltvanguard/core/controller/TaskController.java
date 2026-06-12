package com.voltvanguard.core.controller;

import com.voltvanguard.core.dto.request.TaskRequest;
import com.voltvanguard.core.dto.request.TaskResultRequest;
import com.voltvanguard.core.dto.response.ApiResponse;
import com.voltvanguard.core.dto.response.TaskResponse;
import com.voltvanguard.core.enums.TaskStatus;
import com.voltvanguard.core.enums.TaskType;
import com.voltvanguard.core.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RESTful API for AutonomousTask lifecycle management.
 *
 * <p>The /claim endpoint is designed for internal use by Python AI agents.
 * The /result endpoint serves as the agent callback once processing completes.</p>
 *
 * Base path: /api/v1/tasks
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Autonomous task dispatch and lifecycle management")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @Operation(summary = "Create a new autonomous task for an EV")
    public ResponseEntity<ApiResponse<TaskResponse>> create(
        @Valid @RequestBody TaskRequest request
    ) {
        TaskResponse response = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Task queued"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by UUID")
    public ResponseEntity<ApiResponse<TaskResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.findById(id)));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "List all tasks for a specific EV")
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> findByVehicle(
        @PathVariable UUID vehicleId,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.findByVehicle(vehicleId, pageable)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List tasks filtered by status")
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> findByStatus(
        @PathVariable TaskStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.findByStatus(status, pageable)));
    }

    /**
     * AI agent claim endpoint — atomically transitions the next N PENDING
     * tasks to IN_PROGRESS and returns them for processing.
     */
    @PostMapping("/claim")
    @Operation(summary = "[AI Agent] Claim next batch of pending tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> claim(
        @RequestParam TaskType taskType,
        @RequestParam(defaultValue = "5") int batchSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.claimNextPending(taskType, batchSize)));
    }

    /**
     * AI agent result callback — records outcome and transitions task to
     * COMPLETED or FAILED.
     */
    @PutMapping("/{id}/result")
    @Operation(summary = "[AI Agent] Submit task processing result")
    public ResponseEntity<ApiResponse<TaskResponse>> submitResult(
        @PathVariable UUID id,
        @Valid @RequestBody TaskResultRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.submitResult(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending or in-progress task")
    public ResponseEntity<ApiResponse<TaskResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.cancel(id), "Task cancelled"));
    }
}

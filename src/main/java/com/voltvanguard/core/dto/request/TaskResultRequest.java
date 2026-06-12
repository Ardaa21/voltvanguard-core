package com.voltvanguard.core.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Callback payload from the Python AI agent once a task is processed.
 * The agent POSTs this to PUT /tasks/{id}/result.
 */
public record TaskResultRequest(

    @NotBlank(message = "Agent ID is required")
    String agentId,

    /** true = COMPLETED, false = FAILED */
    boolean success,

    /** JSON result on success; null on failure. */
    String result,

    /** Human-readable error message on failure; null on success. */
    String errorMessage
) {}

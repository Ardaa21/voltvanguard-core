package com.voltvanguard.core.enums;

/**
 * Lifecycle states of an AutonomousTask as it progresses through the system.
 */
public enum TaskStatus {

    /** Task has been created and is waiting to be picked up by an AI agent. */
    PENDING,

    /** Task has been dispatched to an AI agent and is actively running. */
    IN_PROGRESS,

    /** Task completed successfully; result is available. */
    COMPLETED,

    /** Task execution failed; error details stored in the result field. */
    FAILED,

    /** Task was explicitly cancelled before completion. */
    CANCELLED,

    /** Task was not picked up before its scheduled deadline and has expired. */
    EXPIRED
}

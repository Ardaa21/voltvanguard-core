package com.voltvanguard.core.enums;

/**
 * Represents the operational state of an Electric Vehicle within the VoltVanguard ecosystem.
 * Transitions are managed by the VehicleService and updated via telemetry events.
 */
public enum VehicleStatus {

    /** Vehicle is connected and available — idle, ready for tasks or navigation. */
    ONLINE,

    /** Vehicle is idle and ready to accept tasks or charging commands. */
    IDLE,

    /** Vehicle is actively charging at a station. */
    CHARGING,

    /** Vehicle is in motion following an autonomous or manual route. */
    IN_TRANSIT,

    /** Vehicle is awaiting an autonomous task assignment from the AI agent layer. */
    AWAITING_TASK,

    /** Vehicle has a critically low battery level requiring immediate attention. */
    BATTERY_CRITICAL,

    /** Vehicle is offline or unreachable by the telemetry gateway. */
    OFFLINE
}

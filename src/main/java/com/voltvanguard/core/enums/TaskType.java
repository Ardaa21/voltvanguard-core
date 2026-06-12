package com.voltvanguard.core.enums;

/**
 * Classifies the nature of an AutonomousTask delegated to the AI agent layer.
 * Each type maps to a dedicated Python microservice agent.
 */
public enum TaskType {

    /** AI route optimization considering traffic, battery, and charging availability. */
    ROUTE_OPTIMIZATION,

    /** Predictive scheduling of a charging session at an optimal station. */
    CHARGE_SCHEDULING,

    /** Grid load balancing — coordinates multiple vehicles with the energy grid. */
    GRID_BALANCING,

    /** Real-time battery health diagnostics and anomaly detection. */
    BATTERY_DIAGNOSTICS,

    /** Fleet-wide telemetry aggregation and KPI computation. */
    TELEMETRY_AGGREGATION
}

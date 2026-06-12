package com.voltvanguard.core.enums;

/**
 * Operational state of a Charging Station within the grid network.
 */
public enum StationStatus {

    /** Station has at least one available connector. */
    AVAILABLE,

    /** All connectors are actively occupied. */
    BUSY,

    /** Station is temporarily unavailable (planned downtime). */
    OFFLINE,

    /** Station is undergoing maintenance and not accepting connections. */
    MAINTENANCE,

    /** Station is reserved by an autonomous routing decision. */
    RESERVED
}

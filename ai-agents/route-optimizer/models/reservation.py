"""
Pydantic models for the VoltVanguard Backend API.

Mirrors the Spring Boot DTOs in:
  services/vehicle-service/.../dto/request/TaskRequest.java
  services/vehicle-service/.../dto/response/TaskResponse.java
  services/charging-service/.../dto/response/StationResponse.java
"""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Any, Optional
from uuid import UUID

from pydantic import BaseModel, Field


# ── Station (from GET /stations/nearby/available) ─────────────────────────────

class StationStatus(str, Enum):
    AVAILABLE   = "AVAILABLE"
    BUSY        = "BUSY"
    OFFLINE     = "OFFLINE"
    MAINTENANCE = "MAINTENANCE"
    RESERVED    = "RESERVED"


class StationResponse(BaseModel):
    """Mirrors ChargingStation DTO returned by the backend."""
    id:                  UUID
    name:                str
    address:             Optional[str]         = None
    city:                Optional[str]         = None
    latitude:            Decimal
    longitude:           Decimal
    total_connectors:    int                   = Field(alias="totalConnectors")
    available_connectors: int                  = Field(alias="availableConnectors")
    power_kw:            Decimal               = Field(alias="powerKw")
    price_per_kwh_cents: Optional[int]         = Field(None, alias="pricePerKwhCents")
    operator_name:       Optional[str]         = Field(None, alias="operatorName")
    status:              StationStatus
    occupancy_rate:      float                 = Field(alias="occupancyRate")

    model_config = {"populate_by_name": True}

    @property
    def distance_display(self) -> str:
        return f"{self.power_kw} kW | {self.available_connectors}/{self.total_connectors} connectors"


# ── Task / Reservation Request (POST /tasks) ──────────────────────────────────

class TaskType(str, Enum):
    ROUTE_OPTIMIZATION  = "ROUTE_OPTIMIZATION"
    CHARGE_SCHEDULING   = "CHARGE_SCHEDULING"
    GRID_BALANCING      = "GRID_BALANCING"
    BATTERY_DIAGNOSTICS = "BATTERY_DIAGNOSTICS"


class ReservationRequest(BaseModel):
    """
    Maps to TaskRequest sent to POST /api/v1/tasks.
    The payload JSON encodes the charge scheduling context.
    """
    vehicle_id:   UUID      = Field(alias="vehicleId")
    task_type:    TaskType  = Field(TaskType.CHARGE_SCHEDULING, alias="taskType")
    priority:     int       = Field(3)          # 1=highest; critical → priority 1
    scheduled_at: Optional[datetime] = Field(None, alias="scheduledAt")
    expires_at:   Optional[datetime] = Field(None, alias="expiresAt")
    payload:      str                           # JSON string

    model_config = {"populate_by_name": True}


class ReservationPayload(BaseModel):
    """
    Structured payload embedded inside ReservationRequest.payload (serialised to JSON).
    Consumed by the AI Charge Scheduling agent.
    """
    station_id:              UUID
    station_name:            str
    station_latitude:        float
    station_longitude:       float
    vehicle_battery_percent: float
    requested_charge_to_pct: int              = 80   # charge up to 80%
    urgency:                 str              = "medium"
    reasoning:               str              = ""
    agent_id:                str              = ""
    estimated_range_km:      Optional[float]  = None


# ── API Envelope ──────────────────────────────────────────────────────────────

class ApiResponse(BaseModel):
    """Generic wrapper returned by the Spring Boot backend for all endpoints."""
    success:   bool
    data:      Optional[Any] = None
    error:     Optional[str] = None
    message:   Optional[str] = None
    timestamp: Optional[datetime] = None


class TaskResponse(BaseModel):
    """Mirrors AutonomousTask DTO returned by POST /tasks."""
    id:         UUID
    vehicle_id: UUID   = Field(alias="vehicleId")
    task_type:  str    = Field(alias="taskType")
    status:     str
    priority:   int
    created_at: Optional[datetime] = Field(None, alias="createdAt")

    model_config = {"populate_by_name": True}

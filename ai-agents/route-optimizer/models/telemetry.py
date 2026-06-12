"""
Pydantic models mirroring the Spring Boot VehicleTelemetryEvent.
Must stay in sync with the Java class in:
  services/telemetry-service/.../kafka/event/VehicleTelemetryEvent.java
"""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator


class TelemetrySource(str, Enum):
    REAL      = "REAL"
    SIMULATED = "SIMULATED"


class VehicleStatus(str, Enum):
    IDLE             = "IDLE"
    CHARGING         = "CHARGING"
    IN_TRANSIT       = "IN_TRANSIT"
    AWAITING_TASK    = "AWAITING_TASK"
    BATTERY_CRITICAL = "BATTERY_CRITICAL"
    OFFLINE          = "OFFLINE"


class VehicleTelemetryEvent(BaseModel):
    """
    Deserialized Kafka message from topic ``telemetry.raw``.
    All fields are optional (except vehicleId) to survive schema evolution.
    """

    vehicle_id:   UUID = Field(alias="vehicleId")
    vin:          Optional[str] = Field(None, alias="vin")

    # ── Battery ───────────────────────────────────────────────────────────────
    battery_percent:    Optional[Decimal] = Field(None, alias="batteryPercent")
    estimated_range_km: Optional[Decimal] = Field(None, alias="estimatedRangeKm")

    # ── Location ──────────────────────────────────────────────────────────────
    latitude:   Optional[Decimal] = Field(None, alias="latitude")
    longitude:  Optional[Decimal] = Field(None, alias="longitude")

    # ── Motion ────────────────────────────────────────────────────────────────
    speed_kmh:                   Optional[Decimal] = Field(None, alias="speedKmh")
    battery_temperature_celsius: Optional[Decimal] = Field(None, alias="batteryTemperatureCelsius")
    odometer_km:                 Optional[Decimal] = Field(None, alias="odometerKm")

    # ── Status & Metadata ─────────────────────────────────────────────────────
    status:          Optional[VehicleStatus]    = Field(None)
    source_type:     Optional[TelemetrySource]  = Field(None, alias="sourceType")
    gateway_version: Optional[str]              = Field(None, alias="gatewayVersion")

    # ── Timing ────────────────────────────────────────────────────────────────
    captured_at:     Optional[datetime] = Field(None, alias="capturedAt")
    published_at:    Optional[datetime] = Field(None, alias="publishedAt")
    sequence_number: int                = Field(0,    alias="sequenceNumber")

    model_config = {"populate_by_name": True}

    # ── Validators ────────────────────────────────────────────────────────────

    @field_validator("battery_percent")
    @classmethod
    def clamp_battery(cls, v: Optional[Decimal]) -> Optional[Decimal]:
        if v is None:
            return v
        return max(Decimal("0"), min(Decimal("100"), v))

    @model_validator(mode="after")
    def validate_location(self) -> "VehicleTelemetryEvent":
        lat, lng = self.latitude, self.longitude
        if (lat is None) != (lng is None):
            raise ValueError("latitude and longitude must both be present or both absent")
        return self

    # ── Derived Properties ────────────────────────────────────────────────────

    @property
    def has_location(self) -> bool:
        return self.latitude is not None and self.longitude is not None

    @property
    def battery_float(self) -> Optional[float]:
        return float(self.battery_percent) if self.battery_percent is not None else None

    @property
    def is_charging(self) -> bool:
        return self.status == VehicleStatus.CHARGING

    def is_battery_below(self, threshold: float) -> bool:
        pct = self.battery_float
        return pct is not None and pct <= threshold

    def __repr__(self) -> str:
        return (
            f"TelemetryEvent(vehicle={self.vehicle_id}, "
            f"battery={self.battery_percent}%, "
            f"status={self.status}, "
            f"seq={self.sequence_number})"
        )

"""
Creates charge scheduling reservations by POSTing to the VoltVanguard backend.

The backend receives a CHARGE_SCHEDULING AutonomousTask (Phase 1 / TaskController).
The task payload is a structured JSON blob that the downstream AI agents consume.

Deduplication:
  A vehicle cannot receive more than one reservation within
  RESERVATION_COOLDOWN_MINUTES (default 30). This prevents reservation storms
  if the same vehicle is repeatedly evaluated as needing a charge.
"""
from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from typing import Optional
from uuid import UUID

import httpx
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
    before_sleep_log,
)

from config.settings import settings
from models.reservation import (
    ApiResponse,
    ReservationPayload,
    ReservationRequest,
    StationResponse,
    TaskResponse,
    TaskType,
)
from services.decision_engine import ChargingDecision
from utils.logger import get_logger

_log = get_logger("services.reservation_service")


class ReservationService:
    """
    HTTP client for POST /tasks (CHARGE_SCHEDULING).

    Maintains an in-memory cooldown map to prevent duplicate reservations.
    The map is ephemeral — resets on agent restart.
    For persistent deduplication, move the cooldown state to Redis.
    """

    def __init__(self) -> None:
        self._client = httpx.Client(
            base_url=settings.backend.base_url,
            timeout=settings.backend.timeout_seconds,
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        # vehicle_id → last reservation timestamp
        self._cooldown_map: dict[UUID, datetime] = {}
        self._cooldown_minutes = settings.decision.reservation_cooldown_minutes

        _log.info(
            "ReservationService initialised",
            base_url=settings.backend.base_url,
            cooldown_minutes=self._cooldown_minutes,
        )

    # ── Public ────────────────────────────────────────────────────────────────

    def create(
        self,
        vehicle_id: UUID,
        station:    StationResponse,
        decision:   ChargingDecision,
        battery_pct: float,
        estimated_range_km: Optional[float] = None,
    ) -> Optional[TaskResponse]:
        """
        Create a CHARGE_SCHEDULING task for the vehicle.

        Returns the created TaskResponse on success, None on failure or cooldown.

        Args:
            vehicle_id:         Vehicle UUID.
            station:            Best available station selected by StationFinder.
            decision:           ChargingDecision from DecisionEngine (carries urgency/reasoning).
            battery_pct:        Current battery percentage (for payload context).
            estimated_range_km: Optional remaining range estimate.
        """
        # ── Deduplication guard ───────────────────────────────────────────────
        if self._is_on_cooldown(vehicle_id):
            last = self._cooldown_map[vehicle_id]
            _log.info(
                "Reservation skipped — cooldown active",
                vehicle_id=str(vehicle_id),
                last_reservation=last.isoformat(),
                cooldown_minutes=self._cooldown_minutes,
            )
            return None

        # ── Build payload ─────────────────────────────────────────────────────
        payload = ReservationPayload(
            station_id=station.id,
            station_name=station.name,
            station_latitude=float(station.latitude),
            station_longitude=float(station.longitude),
            vehicle_battery_percent=battery_pct,
            requested_charge_to_pct=decision.recommended_charge_to_pct,
            urgency=decision.urgency.value,
            reasoning=decision.reasoning,
            agent_id=settings.agent_id,
            estimated_range_km=estimated_range_km,
        )

        # Priority: critical→1, high→2, medium→3
        priority_map = {"critical": 1, "high": 2, "medium": 3, "low": 4, "none": 5}
        priority = priority_map.get(decision.urgency.value, 3)

        request = ReservationRequest(
            vehicleId=vehicle_id,
            taskType=TaskType.CHARGE_SCHEDULING,
            priority=priority,
            payload=payload.model_dump_json(),
        )

        # ── POST to backend ───────────────────────────────────────────────────
        task_response = self._post_task(request)

        if task_response:
            self._cooldown_map[vehicle_id] = datetime.now(tz=timezone.utc)
            _log.info(
                "Reservation created successfully ✓",
                task_id=str(task_response.id),
                vehicle_id=str(vehicle_id),
                station_name=station.name,
                station_id=str(station.id),
                urgency=decision.urgency.value,
                priority=priority,
                charge_to_pct=decision.recommended_charge_to_pct,
                battery_at_decision=battery_pct,
            )

        return task_response

    def close(self) -> None:
        self._client.close()

    # ── Private: HTTP ─────────────────────────────────────────────────────────

    @retry(
        retry=retry_if_exception_type((httpx.TransportError, httpx.TimeoutException)),
        stop=stop_after_attempt(settings.backend.max_retries),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        before_sleep=before_sleep_log(_log, "WARNING"),   # type: ignore[arg-type]
    )
    def _post_task(self, request: ReservationRequest) -> Optional[TaskResponse]:
        """
        POST /tasks with CHARGE_SCHEDULING payload.
        Retried on transient network failures via tenacity.
        """
        body = request.model_dump(by_alias=True, exclude_none=True)

        _log.debug(
            "Posting reservation task",
            vehicle_id=str(request.vehicle_id),
            task_type=request.task_type.value,
            priority=request.priority,
        )

        try:
            resp = self._client.post("/tasks", json=body)
            resp.raise_for_status()

            envelope = ApiResponse.model_validate(resp.json())

            if not envelope.success or envelope.data is None:
                _log.error(
                    "Backend rejected reservation",
                    error=envelope.error,
                    message=envelope.message,
                )
                return None

            task = TaskResponse.model_validate(envelope.data)
            return task

        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code

            if status == 409:
                _log.warning(
                    "Reservation conflict — vehicle may already have active task",
                    vehicle_id=str(request.vehicle_id),
                    status_code=status,
                )
            elif status == 422:
                _log.error(
                    "Reservation rejected — business rule violation",
                    vehicle_id=str(request.vehicle_id),
                    status_code=status,
                    response=exc.response.text[:300],
                )
            else:
                _log.error(
                    "Backend HTTP error",
                    status_code=status,
                    response=exc.response.text[:300],
                )
            return None

        except Exception as exc:
            _log.error(
                "Unexpected error posting reservation",
                error=str(exc),
                exc_info=True,
            )
            return None

    # ── Private: Cooldown ─────────────────────────────────────────────────────

    def _is_on_cooldown(self, vehicle_id: UUID) -> bool:
        last = self._cooldown_map.get(vehicle_id)
        if last is None:
            return False
        cutoff = datetime.now(tz=timezone.utc) - timedelta(minutes=self._cooldown_minutes)
        return last > cutoff

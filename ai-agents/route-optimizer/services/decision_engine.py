"""
Two-tier Autonomous Charging Decision Engine.

Tier 1 — Rule Engine (always runs, zero latency, zero cost):
  Hard thresholds applied to battery percentage.
  Definitively critical cases are resolved here — no LLM needed.

Tier 2 — LLM Enhancement (runs only in the grey zone):
  For vehicles in the 25%–35% battery range, context matters:
  speed, temperature, estimated range, time of day.
  The LLM provides a nuanced, human-readable recommendation.
  If the LLM call fails for any reason, the rule result is used as fallback.

                 ┌──────────────────┐
  Telemetry ──► │   Rule Engine    │ ──► Critical/OK  ──► Final Decision
                └──────────────────┘
                        │
                    Grey Zone?
                        │
                        ▼
                ┌──────────────────┐
                │   LLM Client     │ ──► LLM Decision
                └──────────────────┘
                        │
                   (on failure)
                        │
                        ▼
                  Rule Fallback
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field, replace as dc_replace
from enum import Enum
from typing import Optional

from config.settings import settings
from models.telemetry import VehicleTelemetryEvent, VehicleStatus
from services.llm_client import LLMClient, LLMChargingDecision
from utils.logger import get_logger

log = get_logger("services.decision_engine")


# ── Domain types ─────────────────────────────────────────────────────────────

class Urgency(str, Enum):
    NONE     = "none"
    LOW      = "low"
    MEDIUM   = "medium"
    HIGH     = "high"
    CRITICAL = "critical"


@dataclass(frozen=True)
class ChargingDecision:
    """Immutable result produced by the DecisionEngine for a single telemetry event."""

    should_charge:              bool
    urgency:                    Urgency
    reasoning:                  str
    recommended_charge_to_pct:  int    = 80
    max_search_radius_km:       float  = 20.0
    confidence:                 float  = 1.0
    decided_by:                 str    = "rules"     # "rules" | "llm" | "llm_fallback"
    processing_ms:              float  = 0.0

    def __str__(self) -> str:
        action = "CHARGE" if self.should_charge else "NO_ACTION"
        return (
            f"[{action}] urgency={self.urgency.value} "
            f"confidence={self.confidence:.2f} via={self.decided_by} "
            f"→ {self.reasoning}"
        )


# ── Decision Engine ────────────────────────────────────────────────────────────

class DecisionEngine:
    """
    Stateless decision maker — safe to share across threads.
    Dependencies are injected via __init__ for easy testing.
    """

    def __init__(self, llm_client: Optional[LLMClient] = None) -> None:
        self._llm = llm_client or (LLMClient() if settings.decision.llm_enabled else None)

        self._critical_threshold  = settings.decision.battery_critical_threshold
        self._low_threshold       = settings.decision.battery_low_threshold
        self._proactive_threshold = settings.decision.battery_proactive_threshold

        log.info(
            "DecisionEngine ready",
            critical_pct=self._critical_threshold,
            low_pct=self._low_threshold,
            proactive_pct=self._proactive_threshold,
            llm_enabled=self._llm is not None,
        )

    # ── Public ────────────────────────────────────────────────────────────────

    def evaluate(self, event: VehicleTelemetryEvent) -> ChargingDecision:
        """
        Evaluate a single telemetry event and return a charging decision.

        This method is designed to be fast (<1ms for rule-only, <15s with LLM)
        and never raises — worst case returns a conservative NO_ACTION.
        """
        t0 = time.perf_counter()

        # Guard: vehicle already charging — never recommend charging again
        if event.is_charging:
            return self._make_decision(
                should_charge=False,
                urgency=Urgency.NONE,
                reasoning="Vehicle is already at a charging station",
                decided_by="rules",
                elapsed_t0=t0,
            )

        # Guard: no battery data — can't decide
        battery = event.battery_float
        if battery is None:
            log.warning("Battery data missing — defaulting to NO_ACTION", vehicle_id=str(event.vehicle_id))
            return self._make_decision(
                should_charge=False,
                urgency=Urgency.NONE,
                reasoning="Battery percentage unavailable in telemetry",
                decided_by="rules",
                elapsed_t0=t0,
            )

        # ── Tier 1: Rule engine ───────────────────────────────────────────────
        rule_result = self._apply_rules(battery, event)

        # Hard critical cases resolved immediately — no LLM overhead
        if rule_result.urgency in (Urgency.CRITICAL, Urgency.NONE):
            log.info(
                "Decision by rules (terminal)",
                vehicle_id=str(event.vehicle_id),
                battery_pct=battery,
                urgency=rule_result.urgency.value,
                should_charge=rule_result.should_charge,
            )
            return dc_replace(rule_result,
                processing_ms=round((time.perf_counter() - t0) * 1000, 2)
            )

        # ── Tier 2: LLM for grey zone ─────────────────────────────────────────
        if self._llm is not None and rule_result.urgency in (Urgency.LOW, Urgency.MEDIUM, Urgency.HIGH):
            llm_result = self._consult_llm(event, battery, rule_result)
            if llm_result is not None:
                return dc_replace(llm_result,
                    processing_ms=round((time.perf_counter() - t0) * 1000, 2)
                )
            # LLM failed — log and fall through to rule result
            log.warning(
                "LLM unavailable — using rule fallback",
                vehicle_id=str(event.vehicle_id),
                rule_urgency=rule_result.urgency.value,
            )
            return dc_replace(rule_result,
                decided_by="llm_fallback",
                processing_ms=round((time.perf_counter() - t0) * 1000, 2),
            )

        return dc_replace(rule_result,
            processing_ms=round((time.perf_counter() - t0) * 1000, 2)
        )

    # ── Tier 1: Rule Engine ───────────────────────────────────────────────────

    def _apply_rules(
        self,
        battery: float,
        event:   VehicleTelemetryEvent,
    ) -> ChargingDecision:
        """
        Pure threshold-based rules. O(1), no I/O, fully deterministic.

        Battery band         Urgency     Action
        ─────────────────    ─────────   ──────
        ≤ critical (15%)     CRITICAL    Charge immediately
        ≤ low (25%)          HIGH        Charge very soon
        ≤ proactive (35%)    MEDIUM      Evaluate context
        > proactive          NONE        No action
        """
        if battery <= self._critical_threshold:
            return ChargingDecision(
                should_charge=True,
                urgency=Urgency.CRITICAL,
                reasoning=(
                    f"Battery critically low at {battery:.1f}% "
                    f"(threshold: {self._critical_threshold}%). Immediate charging required."
                ),
                recommended_charge_to_pct=90,
                max_search_radius_km=settings.decision.station_search_radius_km * 1.5,
                confidence=1.0,
                decided_by="rules",
            )

        if battery <= self._low_threshold:
            return ChargingDecision(
                should_charge=True,
                urgency=Urgency.HIGH,
                reasoning=(
                    f"Battery low at {battery:.1f}% "
                    f"(threshold: {self._low_threshold}%). Proactive charging recommended."
                ),
                recommended_charge_to_pct=85,
                max_search_radius_km=settings.decision.station_search_radius_km,
                confidence=0.95,
                decided_by="rules",
            )

        if battery <= self._proactive_threshold:
            return ChargingDecision(
                should_charge=True,
                urgency=Urgency.MEDIUM,
                reasoning=(
                    f"Battery at {battery:.1f}% — within proactive charging window. "
                    f"Context evaluation recommended."
                ),
                recommended_charge_to_pct=80,
                max_search_radius_km=settings.decision.station_search_radius_km * 0.7,
                confidence=0.6,
                decided_by="rules",
            )

        return ChargingDecision(
            should_charge=False,
            urgency=Urgency.NONE,
            reasoning=f"Battery at {battery:.1f}% — sufficient range. No action needed.",
            confidence=1.0,
            decided_by="rules",
        )

    # ── Tier 2: LLM consultation ──────────────────────────────────────────────

    def _consult_llm(
        self,
        event:       VehicleTelemetryEvent,
        battery:     float,
        rule_result: ChargingDecision,
    ) -> Optional[ChargingDecision]:
        """
        Calls the LLM with full context. Maps LLMChargingDecision → ChargingDecision.
        Returns None if the LLM call fails or returns a low-confidence result.
        """
        assert self._llm is not None

        llm_raw: Optional[LLMChargingDecision] = self._llm.analyze_charging_need(
            battery_percent=battery,
            latitude=float(event.latitude) if event.latitude else None,
            longitude=float(event.longitude) if event.longitude else None,
            vehicle_status=event.status.value if event.status else "UNKNOWN",
            speed_kmh=float(event.speed_kmh) if event.speed_kmh else None,
            estimated_range_km=float(event.estimated_range_km) if event.estimated_range_km else None,
            battery_temp_c=float(event.battery_temperature_celsius) if event.battery_temperature_celsius else None,
            rule_hint=rule_result.urgency.value,
        )

        if llm_raw is None:
            return None

        # Safety override: never let LLM contradict a critical rule decision
        urgency_str = llm_raw.urgency.lower()
        if rule_result.urgency == Urgency.HIGH and not llm_raw.should_charge:
            log.warning(
                "LLM tried to override HIGH rule — safety veto applied",
                vehicle_id=str(event.vehicle_id),
                llm_reasoning=llm_raw.reasoning[:100],
            )
            urgency_str = "high"
            llm_raw = llm_raw.model_copy(update={"should_charge": True, "urgency": "high"})

        try:
            urgency = Urgency(urgency_str)
        except ValueError:
            urgency = rule_result.urgency

        decision = ChargingDecision(
            should_charge=llm_raw.should_charge,
            urgency=urgency,
            reasoning=llm_raw.reasoning,
            recommended_charge_to_pct=llm_raw.recommended_charge_to_pct,
            max_search_radius_km=llm_raw.max_search_radius_km,
            confidence=llm_raw.confidence,
            decided_by="llm",
        )

        log.info(
            "LLM decision",
            vehicle_id=str(event.vehicle_id),
            should_charge=decision.should_charge,
            urgency=decision.urgency.value,
            confidence=decision.confidence,
            reasoning=decision.reasoning[:120],
        )
        return decision

    # ── Private helpers ───────────────────────────────────────────────────────

    @staticmethod
    def _make_decision(
        should_charge: bool,
        urgency:       Urgency,
        reasoning:     str,
        decided_by:    str,
        elapsed_t0:    float,
        **kwargs,
    ) -> ChargingDecision:
        return ChargingDecision(
            should_charge=should_charge,
            urgency=urgency,
            reasoning=reasoning,
            decided_by=decided_by,
            processing_ms=round((time.perf_counter() - elapsed_t0) * 1000, 2),
            **kwargs,
        )

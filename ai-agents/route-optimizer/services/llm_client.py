"""
OpenAI LLM client with structured output and robust fallback handling.

Design principles:
  - Structured JSON output (response_format=json_object) eliminates parsing guesswork.
  - System prompt is the single source of truth for agent behaviour.
  - Every call is timed and logged; token usage is tracked for cost monitoring.
  - LLM failures are non-fatal: callers receive None and fall back to rule-based logic.
"""
from __future__ import annotations

import json
import time
from typing import Optional

from openai import OpenAI, OpenAIError
from pydantic import BaseModel, ValidationError

from config.settings import settings
from utils.logger import get_logger

log = get_logger("services.llm_client")


# ── Structured response model ─────────────────────────────────────────────────

class LLMChargingDecision(BaseModel):
    """
    JSON structure the LLM must return.
    Validated by Pydantic before the caller sees it.
    """
    should_charge:              bool
    urgency:                    str       # none | low | medium | high | critical
    reasoning:                  str       # max ~200 chars; shown in logs
    recommended_charge_to_pct:  int       # target SOC%
    max_search_radius_km:       float
    confidence:                 float     # 0.0 – 1.0; low confidence → prefer rules


# ── System prompt ──────────────────────────────────────────────────────────────

_SYSTEM_PROMPT = """\
You are VoltVanguard's autonomous EV Charge Decision Agent.

Your role: decide whether an electric vehicle needs to visit a charging station NOW
based on its current telemetry and contextual data.

RULES (must follow strictly):
1. battery ≤ 15% → should_charge=true, urgency="critical"
2. battery ≤ 25% → should_charge=true, urgency="high"
3. battery ≤ 35% → weigh context (weather, speed, location remoteness) → medium/low
4. battery > 35% → should_charge=false unless exceptional context
5. If vehicle is already CHARGING → should_charge=false

OUTPUT FORMAT — respond ONLY with valid JSON, no prose:
{
  "should_charge": <bool>,
  "urgency": "<none|low|medium|high|critical>",
  "reasoning": "<≤200 chars explaining decision>",
  "recommended_charge_to_pct": <int 50-100>,
  "max_search_radius_km": <float>,
  "confidence": <float 0.0-1.0>
}
"""


# ── LLM Client ────────────────────────────────────────────────────────────────

class LLMClient:
    """Thin wrapper around OpenAI's chat completions with structured output."""

    def __init__(self) -> None:
        self._client = OpenAI(
            api_key=settings.openai.api_key,
            timeout=settings.openai.timeout_seconds,
        )
        self._model       = settings.openai.model
        self._max_tokens  = settings.openai.max_tokens
        self._temperature = settings.openai.temperature

        log.info(
            "LLM client initialised",
            model=self._model,
            temperature=self._temperature,
        )

    def analyze_charging_need(
        self,
        battery_percent:    float,
        latitude:           Optional[float],
        longitude:          Optional[float],
        vehicle_status:     str,
        speed_kmh:          Optional[float],
        estimated_range_km: Optional[float],
        battery_temp_c:     Optional[float],
        rule_hint:          str,
    ) -> Optional[LLMChargingDecision]:
        """
        Ask the LLM whether the vehicle should charge.

        Returns None on any failure — callers must implement a rule-based fallback.

        Args:
            battery_percent:    Current SOC.
            latitude/longitude: Vehicle position (may be None).
            vehicle_status:     IDLE | IN_TRANSIT | CHARGING etc.
            speed_kmh:          Current speed (proxy for energy demand).
            estimated_range_km: AI-predicted remaining range.
            battery_temp_c:     Battery temperature (high temp = accelerated degradation).
            rule_hint:          Pre-computed rule result ("critical", "low", "ok").
        """
        if not settings.openai.api_key:
            log.warning("LLM skipped — OPENAI_API_KEY not set")
            return None

        user_message = self._build_user_message(
            battery_percent, latitude, longitude, vehicle_status,
            speed_kmh, estimated_range_km, battery_temp_c, rule_hint,
        )

        t0 = time.perf_counter()
        try:
            response = self._client.chat.completions.create(
                model=self._model,
                messages=[
                    {"role": "system", "content": _SYSTEM_PROMPT},
                    {"role": "user",   "content": user_message},
                ],
                response_format={"type": "json_object"},
                max_tokens=self._max_tokens,
                temperature=self._temperature,
            )

            elapsed_ms = (time.perf_counter() - t0) * 1000
            usage      = response.usage

            log.debug(
                "LLM call completed",
                elapsed_ms=round(elapsed_ms, 1),
                prompt_tokens=usage.prompt_tokens if usage else "?",
                completion_tokens=usage.completion_tokens if usage else "?",
            )

            raw_content = response.choices[0].message.content or ""
            return self._parse_response(raw_content)

        except OpenAIError as exc:
            log.warning("OpenAI API error — falling back to rule engine", error=str(exc))
            return None
        except Exception as exc:
            log.error("Unexpected LLM error", error=str(exc), exc_info=True)
            return None

    # ── Private ────────────────────────────────────────────────────────────────

    @staticmethod
    def _build_user_message(
        battery_percent:    float,
        latitude:           Optional[float],
        longitude:          Optional[float],
        vehicle_status:     str,
        speed_kmh:          Optional[float],
        estimated_range_km: Optional[float],
        battery_temp_c:     Optional[float],
        rule_hint:          str,
    ) -> str:
        location_str = (
            f"{latitude:.6f}, {longitude:.6f}"
            if latitude is not None and longitude is not None
            else "unknown"
        )
        return (
            f"Vehicle telemetry snapshot:\n"
            f"  battery_percent:    {battery_percent:.1f}%\n"
            f"  status:             {vehicle_status}\n"
            f"  speed_kmh:          {speed_kmh or 'unknown'}\n"
            f"  estimated_range_km: {estimated_range_km or 'unknown'}\n"
            f"  battery_temp_c:     {battery_temp_c or 'unknown'}\n"
            f"  location:           {location_str}\n"
            f"  rule_engine_hint:   {rule_hint}\n\n"
            f"Apply the RULES from the system prompt and output your JSON decision."
        )

    @staticmethod
    def _parse_response(raw: str) -> Optional[LLMChargingDecision]:
        """Parse and validate the JSON response from the LLM."""
        try:
            data = json.loads(raw)
            decision = LLMChargingDecision(**data)
            log.debug(
                "LLM decision parsed",
                should_charge=decision.should_charge,
                urgency=decision.urgency,
                confidence=decision.confidence,
                reasoning=decision.reasoning[:80],
            )
            return decision
        except (json.JSONDecodeError, ValidationError) as exc:
            log.warning("LLM response parse failed", raw=raw[:200], error=str(exc))
            return None

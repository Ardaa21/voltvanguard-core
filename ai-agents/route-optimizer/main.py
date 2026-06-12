"""
VoltVanguard Route Optimizer Agent — Entry Point

Startup sequence:
  1. Load and validate all settings (crash early on bad config)
  2. Configure structured logging
  3. Wire up all service dependencies (DI by hand — no framework needed)
  4. Install OS signal handlers for graceful shutdown
  5. Start Kafka consumer polling loop

Shutdown sequence (on SIGTERM / SIGINT / KeyboardInterrupt):
  1. Signal consumer to stop accepting new messages
  2. Finish processing the current batch
  3. Commit final offsets
  4. Flush DLQ producer
  5. Close HTTP clients
  6. Log final metrics summary
  7. Exit 0
"""
from __future__ import annotations

import signal
import sys
import time

from config.settings import settings
from kafka.consumer import TelemetryConsumer
from services.decision_engine import DecisionEngine
from services.llm_client import LLMClient
from services.reservation_service import ReservationService
from services.station_finder import StationFinder
from utils.logger import configure_logging, get_logger

# Logger is configured before first use
configure_logging()
log = get_logger("main")


# ── Startup Banner ─────────────────────────────────────────────────────────────

def _print_banner() -> None:
    banner = r"""
  ____   ____      _ _    __     __                                     _
 |  _ \ / ___| ___| | |_  \ \   / /_ _ _ __   __ _ _   _  __ _ _ __ __| |
 | | | | |    / _ \ | | |  \ \ / / _` | '_ \ / _` | | | |/ _` | '__/ _` |
 | |_| | |___|  __/ | | |   \ V / (_| | | | | (_| | |_| | (_| | | | (_| |
 |____/ \____|\___|\___| |    \_/ \__,_|_| |_|\__, |\__,_|\__,_|_|  \__,_|
  Route Optimizer Agent v1.0.0                |___/  🤖 Autonomous Charging
    """
    print(banner, flush=True)


# ── Dependency Wiring ──────────────────────────────────────────────────────────

def _build_consumer() -> TelemetryConsumer:
    """
    Explicit dependency injection — no IoC container.
    Each service is stateless or thread-safe; only consumer is stateful.
    """
    log.info("Initialising services...")

    llm_client = LLMClient() if settings.decision.llm_enabled else None
    if not settings.decision.llm_enabled:
        log.warning("LLM disabled — pure rule-based mode active")

    decision_engine     = DecisionEngine(llm_client=llm_client)
    station_finder      = StationFinder()
    reservation_service = ReservationService()

    consumer = TelemetryConsumer(
        decision_engine=decision_engine,
        station_finder=station_finder,
        reservation_service=reservation_service,
    )

    log.info(
        "All services ready",
        agent_id=settings.agent_id,
        kafka_topic=settings.kafka.telemetry_topic,
        kafka_group=settings.kafka.consumer_group,
        backend_url=settings.backend.base_url,
        llm_model=settings.openai.model if settings.decision.llm_enabled else "disabled",
        battery_critical_pct=settings.decision.battery_critical_threshold,
        battery_low_pct=settings.decision.battery_low_threshold,
        station_radius_km=settings.decision.station_search_radius_km,
    )

    return consumer


# ── Signal Handling ────────────────────────────────────────────────────────────

def _install_signal_handlers(consumer: TelemetryConsumer) -> None:
    """Install SIGTERM and SIGINT handlers for graceful shutdown."""

    def _handler(signum: int, frame) -> None:  # type: ignore[type-arg]
        sig_name = signal.Signals(signum).name
        log.warning(f"Received {sig_name} — initiating graceful shutdown")
        consumer.stop()

    signal.signal(signal.SIGTERM, _handler)
    signal.signal(signal.SIGINT,  _handler)
    log.debug("Signal handlers installed for SIGTERM / SIGINT")


# ── Config Validation ──────────────────────────────────────────────────────────

def _validate_config() -> None:
    """
    Pre-flight checks on startup configuration.
    Logs warnings for non-fatal issues; raises SystemExit on fatal ones.
    """
    if settings.decision.llm_enabled and not settings.openai.api_key:
        log.error(
            "LLM_ENABLED=true but OPENAI_API_KEY is not set. "
            "Set the key or set LLM_ENABLED=false to run in rule-only mode."
        )
        # Non-fatal: LLMClient.analyze_charging_need already handles missing key gracefully
        log.warning("Continuing in degraded mode — LLM calls will be skipped")

    if settings.decision.battery_critical_threshold >= settings.decision.battery_low_threshold:
        log.error("BATTERY_CRITICAL_THRESHOLD must be < BATTERY_LOW_THRESHOLD")
        sys.exit(1)

    log.info(
        "Configuration validated ✓",
        agent_id=settings.agent_id,
        log_level=settings.log.level,
        log_file=settings.log.file,
    )


# ── Main ───────────────────────────────────────────────────────────────────────

def main() -> None:
    _print_banner()

    log.info("VoltVanguard Route Optimizer starting up", agent_id=settings.agent_id)

    _validate_config()

    consumer = _build_consumer()
    _install_signal_handlers(consumer)

    log.info("Entering Kafka polling loop — press Ctrl+C to stop")
    t_start = time.time()

    try:
        consumer.start()   # Blocks until stop() is called
    except Exception as exc:
        log.critical("Fatal error in consumer loop", error=str(exc), exc_info=True)
        sys.exit(1)
    finally:
        elapsed = round(time.time() - t_start, 1)
        log.info("Agent stopped", uptime_seconds=elapsed)


if __name__ == "__main__":
    main()

"""
Structured logging setup using Loguru.

Design goals:
  1. Every log line carries a correlation_id so distributed traces can be followed.
  2. Production mode serialises to JSON (ELK / Loki ingestion).
  3. Dev mode renders colour-coded, human-readable output via Rich.
  4. All external I/O (Kafka, HTTP, LLM) uses dedicated child loggers so
     log-level filtering is granular per subsystem.

Usage:
    from utils.logger import get_logger, bind_context

    log = get_logger("services.decision_engine")

    with bind_context(vehicle_id="abc", seq=42):
        log.info("Decision made", decision="charge", urgency="high")
"""
from __future__ import annotations

import os
import sys
from contextlib import contextmanager
from typing import Any, Generator

from loguru import logger

from config.settings import settings

# ── Loguru format strings ─────────────────────────────────────────────────────

_HUMAN_FORMAT = (
    "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
    "<level>{level: <8}</level> | "
    "<cyan>{extra[agent_id]}</cyan> | "
    "<magenta>{extra[correlation_id]}</magenta> | "
    "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> | "
    "<level>{message}</level>"
    "{extra[kv_pairs]}"
)

_JSON_FORMAT = "{message}"   # loguru handles the rest when serialize=True


def _kv_pairs_patcher(record: dict[str, Any]) -> None:
    """Append key-value extra fields to the message in human-readable mode."""
    ignored = {"agent_id", "correlation_id", "kv_pairs"}
    kv = {k: v for k, v in record["extra"].items() if k not in ignored}
    record["extra"]["kv_pairs"] = (
        " | " + " ".join(f"{k}={v!r}" for k, v in kv.items()) if kv else ""
    )


def _build_sink_kwargs(serialize: bool) -> dict[str, Any]:
    return {
        "format":     _JSON_FORMAT if serialize else _HUMAN_FORMAT,
        "serialize":  serialize,
        "enqueue":    True,   # thread-safe async queue
        "backtrace":  True,
        "diagnose":   not serialize,  # disable in prod (leaks internals)
    }


def configure_logging() -> None:
    """
    Must be called once at startup (in main.py) before any log calls.
    Re-entrant: safe to call multiple times (removes previous handlers).
    """
    logger.remove()  # Remove default handler

    # Ensure log directory exists
    os.makedirs(os.path.dirname(settings.log.file), exist_ok=True)

    serialize = settings.log.serialize_json

    # ── Stdout sink ──────────────────────────────────────────────────────────
    logger.add(
        sys.stdout,
        level=settings.log.level,
        **_build_sink_kwargs(serialize),
    )

    # ── Rotating file sink ───────────────────────────────────────────────────
    logger.add(
        settings.log.file,
        level=settings.log.level,
        rotation=settings.log.rotation,
        retention=settings.log.retention,
        compression="gz",
        **_build_sink_kwargs(serialize=True),   # always JSON in file for search
    )

    # Patch every record with baseline context fields
    logger.configure(
        patcher=_kv_pairs_patcher,
        extra={
            "agent_id":       settings.agent_id,
            "correlation_id": "–",
            "kv_pairs":       "",
        },
    )

    logger.info(
        "Logging configured",
        level=settings.log.level,
        file=settings.log.file,
        json=serialize,
    )


def get_logger(name: str) -> "logger":  # type: ignore[type-arg]
    """Return a logger bound to a subsystem name."""
    return logger.bind(name=name)


@contextmanager
def bind_context(**kwargs: Any) -> Generator[None, None, None]:
    """
    Context manager that temporarily binds extra fields to all log calls
    within the block. Useful for tying all logs in a processing cycle to
    a specific vehicle_id or correlation_id.

    Example::

        with bind_context(vehicle_id=str(event.vehicle_id), seq=event.sequence_number):
            log.info("Processing started")
            decision = engine.evaluate(event)
            log.info("Decision", urgency=decision.urgency.value)
    """
    with logger.contextualize(**kwargs):
        yield


# Re-export a module-level default logger for convenience
log = get_logger(__name__)

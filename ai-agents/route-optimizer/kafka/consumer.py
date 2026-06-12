"""
Kafka consumer loop for raw EV telemetry.

Responsibilities:
  1. Poll messages from telemetry.raw in a tight, efficient loop.
  2. Deserialize JSON → VehicleTelemetryEvent (Pydantic).
  3. Invoke the full processing pipeline: Decision → StationFinder → Reservation.
  4. Commit offsets manually after successful processing.
  5. Route unprocessable messages to the DLQ after max retries.
  6. Emit per-vehicle and aggregate metrics to stdout.
  7. Shut down cleanly on SIGTERM / SIGINT.

Threading model:
  Single-threaded synchronous polling loop (confluent-kafka recommends this).
  All downstream calls (HTTP to backend, LLM) are blocking but have timeouts.
  For higher throughput, run multiple consumer replicas with the same group-id.
"""
from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Optional

from confluent_kafka import Consumer, KafkaError, KafkaException, Message, Producer
from pydantic import ValidationError

from config.settings import settings
from models.telemetry import VehicleTelemetryEvent
from services.decision_engine import ChargingDecision, DecisionEngine, Urgency
from services.reservation_service import ReservationService
from services.station_finder import StationFinder
from utils.logger import get_logger

_log = get_logger("kafka.consumer")


# ── Runtime metrics (in-process, Prometheus-friendly names) ──────────────────

@dataclass
class ConsumerMetrics:
    messages_received:   int = 0
    messages_processed:  int = 0
    messages_skipped:    int = 0
    messages_dlq:        int = 0
    decisions_charge:    int = 0
    decisions_no_action: int = 0
    reservations_ok:     int = 0
    reservations_failed: int = 0
    processing_errors:   int = 0
    _start_time:         float = field(default_factory=time.time)

    def throughput(self) -> float:
        elapsed = time.time() - self._start_time
        return round(self.messages_processed / elapsed, 2) if elapsed > 0 else 0.0

    def log_summary(self) -> None:
        _log.info(
            "Consumer metrics snapshot",
            received=self.messages_received,
            processed=self.messages_processed,
            skipped=self.messages_skipped,
            dlq=self.messages_dlq,
            charge_decisions=self.decisions_charge,
            no_action=self.decisions_no_action,
            reservations_ok=self.reservations_ok,
            reservations_failed=self.reservations_failed,
            errors=self.processing_errors,
            throughput_per_sec=self.throughput(),
        )


# ── Telemetry Consumer ────────────────────────────────────────────────────────

class TelemetryConsumer:
    """
    Main Kafka consumer. Instantiated once in main.py and run via .start().
    Shutdown is triggered by calling .stop() from a signal handler.
    """

    def __init__(
        self,
        decision_engine:      DecisionEngine,
        station_finder:       StationFinder,
        reservation_service:  ReservationService,
    ) -> None:
        self._engine      = decision_engine
        self._finder      = station_finder
        self._reservation = reservation_service
        self._metrics     = ConsumerMetrics()
        self._running     = False

        # ── Kafka Consumer ────────────────────────────────────────────────────
        consumer_config = {
            "bootstrap.servers":       settings.kafka.bootstrap_servers,
            "group.id":                settings.kafka.consumer_group,
            "auto.offset.reset":       "latest",
            "enable.auto.commit":      False,          # manual commit only
            "max.poll.interval.ms":    300_000,
            "session.timeout.ms":      settings.kafka.session_timeout_ms,
            "heartbeat.interval.ms":   settings.kafka.heartbeat_interval_ms,
            "fetch.max.bytes":         52_428_800,     # 50 MB
            "max.partition.fetch.bytes": 10_485_760,   # 10 MB
        }
        self._consumer = Consumer(consumer_config)

        # ── DLQ Producer ─────────────────────────────────────────────────────
        dlq_config = {
            "bootstrap.servers": settings.kafka.bootstrap_servers,
            "acks":              "all",
            "enable.idempotence": True,
        }
        self._dlq_producer = Producer(dlq_config)

        _log.info(
            "TelemetryConsumer created",
            group=settings.kafka.consumer_group,
            topic=settings.kafka.telemetry_topic,
            agent_id=settings.agent_id,
        )

    # ── Public ────────────────────────────────────────────────────────────────

    def start(self) -> None:
        """Enter the polling loop. Blocks until stop() is called."""
        self._consumer.subscribe(
            [settings.kafka.telemetry_topic],
            on_assign=self._on_partition_assign,
            on_revoke=self._on_partition_revoke,
        )
        self._running = True

        _log.info(
            "Consumer started — entering poll loop",
            topic=settings.kafka.telemetry_topic,
        )

        last_metrics_log = time.time()
        METRICS_INTERVAL = 60  # log metrics every 60 seconds

        try:
            while self._running:
                msg: Optional[Message] = self._consumer.poll(
                    timeout=settings.kafka.poll_timeout_seconds
                )

                if msg is None:
                    # No message in this poll window — healthy idle
                    continue

                if msg.error():
                    self._handle_kafka_error(msg)
                    continue

                self._metrics.messages_received += 1
                self._process_message(msg)

                # Periodic metrics dump
                if time.time() - last_metrics_log >= METRICS_INTERVAL:
                    self._metrics.log_summary()
                    last_metrics_log = time.time()

        except KeyboardInterrupt:
            _log.info("KeyboardInterrupt received — shutting down")
        finally:
            self._shutdown()

    def stop(self) -> None:
        """Signal the polling loop to exit cleanly. Safe to call from any thread."""
        _log.info("Stop signal received")
        self._running = False

    # ── Core Processing ───────────────────────────────────────────────────────

    def _process_message(self, msg: Message) -> None:
        """
        Full pipeline for a single Kafka message:
          Deserialize → Decide → (Find Station → Reserve) → Commit
        """
        raw_value = msg.value()
        partition = msg.partition()
        offset    = msg.offset()

        # ── Step 1: Deserialize ───────────────────────────────────────────────
        event = self._deserialize(raw_value, partition, offset)
        if event is None:
            self._route_to_dlq(msg, reason="deserialization_failed")
            self._consumer.commit(message=msg)
            return

        with _log.contextualize(
            vehicle_id=str(event.vehicle_id),
            seq=event.sequence_number,
            partition=partition,
            offset=offset,
        ):
            _log.debug(
                "Processing message",
                battery_pct=float(event.battery_percent) if event.battery_percent else None,
                status=event.status.value if event.status else None,
            )

            # ── Step 2: Evaluate charging need ────────────────────────────────
            decision = self._engine.evaluate(event)

            _log.info(
                "Decision made",
                should_charge=decision.should_charge,
                urgency=decision.urgency.value,
                decided_by=decision.decided_by,
                processing_ms=decision.processing_ms,
                reasoning=decision.reasoning[:100],
            )

            if decision.should_charge:
                self._metrics.decisions_charge += 1
                self._handle_charge_decision(event, decision)
            else:
                self._metrics.decisions_no_action += 1

        # ── Step 3: Commit offset (success path) ──────────────────────────────
        self._consumer.commit(message=msg)
        self._metrics.messages_processed += 1

    def _handle_charge_decision(
        self,
        event:    VehicleTelemetryEvent,
        decision: ChargingDecision,
    ) -> None:
        """Find nearest station and create a reservation."""

        if not event.has_location:
            _log.warning(
                "Cannot find station — vehicle has no location data",
                vehicle_id=str(event.vehicle_id),
            )
            return

        lat = float(event.latitude)   # type: ignore[arg-type]
        lng = float(event.longitude)  # type: ignore[arg-type]

        # ── Station search ────────────────────────────────────────────────────
        _log.info(
            "Searching for nearest station",
            lat=lat, lng=lng,
            radius_km=decision.max_search_radius_km,
            urgency=decision.urgency.value,
        )

        # For critical urgency: require at least 50 kW (faster charge)
        min_power = 50.0 if decision.urgency == Urgency.CRITICAL else None

        station = self._finder.find_best(
            latitude=lat,
            longitude=lng,
            radius_km=decision.max_search_radius_km,
            require_min_power_kw=min_power,
        )

        if station is None:
            # Widen radius on critical — vehicle life depends on it
            if decision.urgency == Urgency.CRITICAL:
                _log.warning(
                    "No high-power station in range — retrying with wider radius and no power filter",
                    vehicle_id=str(event.vehicle_id),
                )
                station = self._finder.find_best(
                    latitude=lat,
                    longitude=lng,
                    radius_km=decision.max_search_radius_km * 2,
                )

        if station is None:
            _log.error(
                "No available stations found — reservation aborted",
                vehicle_id=str(event.vehicle_id),
                urgency=decision.urgency.value,
            )
            self._metrics.reservations_failed += 1
            return

        # ── Create reservation ────────────────────────────────────────────────
        task = self._reservation.create(
            vehicle_id=event.vehicle_id,
            station=station,
            decision=decision,
            battery_pct=float(event.battery_percent) if event.battery_percent else 0.0,
            estimated_range_km=float(event.estimated_range_km) if event.estimated_range_km else None,
        )

        if task:
            self._metrics.reservations_ok += 1
        else:
            self._metrics.reservations_failed += 1

    # ── DLQ ───────────────────────────────────────────────────────────────────

    def _route_to_dlq(self, msg: Message, reason: str) -> None:
        """Publish an unprocessable message to the Dead Letter Queue."""
        try:
            headers = list(msg.headers() or []) + [
                ("dlq-reason", reason.encode()),
                ("dlq-agent",  settings.agent_id.encode()),
                ("dlq-topic",  (msg.topic() or "").encode()),
                ("dlq-partition", str(msg.partition()).encode()),
                ("dlq-offset",    str(msg.offset()).encode()),
            ]
            self._dlq_producer.produce(
                topic=settings.kafka.dlq_topic,
                key=msg.key(),
                value=msg.value(),
                headers=headers,
            )
            self._dlq_producer.flush(timeout=5)
            self._metrics.messages_dlq += 1
            _log.warning(
                "Message routed to DLQ",
                reason=reason,
                topic=msg.topic(),
                partition=msg.partition(),
                offset=msg.offset(),
            )
        except Exception as exc:
            _log.error("Failed to route message to DLQ", error=str(exc))

    # ── Helpers ───────────────────────────────────────────────────────────────

    @staticmethod
    def _deserialize(
        raw: Optional[bytes],
        partition: int,
        offset: int,
    ) -> Optional[VehicleTelemetryEvent]:
        if raw is None:
            _log.warning("Null message payload", partition=partition, offset=offset)
            return None
        try:
            data = json.loads(raw.decode("utf-8"))
            return VehicleTelemetryEvent.model_validate(data)
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            _log.error(
                "JSON decode failed",
                partition=partition, offset=offset,
                raw=raw[:200] if raw else None,
                error=str(exc),
            )
            return None
        except ValidationError as exc:
            _log.error(
                "Pydantic validation failed",
                partition=partition, offset=offset,
                errors=exc.errors(),
            )
            return None

    def _handle_kafka_error(self, msg: Message) -> None:
        err = msg.error()
        if err.code() == KafkaError._PARTITION_EOF:
            _log.debug(
                "Reached partition EOF",
                topic=msg.topic(), partition=msg.partition(), offset=msg.offset(),
            )
        elif err.code() == KafkaError.UNKNOWN_TOPIC_OR_PART:
            _log.error("Topic not found — check KAFKA_TELEMETRY_TOPIC setting", error=str(err))
        else:
            _log.error("Kafka error", code=err.code(), error=str(err))

    def _on_partition_assign(self, consumer, partitions) -> None:  # type: ignore[type-arg]
        _log.info(
            "Partitions assigned",
            partitions=[f"{p.topic}[{p.partition}]" for p in partitions],
        )

    def _on_partition_revoke(self, consumer, partitions) -> None:  # type: ignore[type-arg]
        _log.info(
            "Partitions revoked",
            partitions=[f"{p.topic}[{p.partition}]" for p in partitions],
        )

    def _shutdown(self) -> None:
        _log.info("Shutting down consumer...")
        self._metrics.log_summary()
        try:
            self._consumer.close()
        except Exception as exc:
            _log.warning("Error closing consumer", error=str(exc))
        try:
            self._dlq_producer.flush(timeout=10)
        except Exception as exc:
            _log.warning("Error flushing DLQ producer", error=str(exc))
        self._finder.close()
        self._reservation.close()
        _log.info("Shutdown complete")

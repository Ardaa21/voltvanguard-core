package com.voltvanguard.core.kafka.consumer;

import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Kafka consumer for raw vehicle telemetry events.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Receive batched telemetry events from {@code telemetry.raw} topic.</li>
 *   <li>Delegate each event to {@link TelemetryProcessingService} for business logic.</li>
 *   <li>Commit offsets manually — only after successful processing.</li>
 *   <li>Measure and expose end-to-end processing latency via Micrometer.</li>
 * </ol>
 *
 * <h3>Why Manual ACK?</h3>
 * <p>With auto-commit, an event is considered "consumed" as soon as it's polled —
 * even if the processor crashes mid-flight. Manual ACK ensures the offset is committed
 * only after we've completed all Redis + DB operations (or explicitly decided to skip).
 * Combined with the {@code DefaultErrorHandler} retry + DLT in {@code KafkaConsumerConfig},
 * this gives us at-least-once delivery semantics with a safety net.</p>
 *
 * <h3>Concurrency Model</h3>
 * <p>Spring Kafka creates {@code concurrency} listener threads (configurable, default 3).
 * Each thread handles one partition exclusively — no shared state issues between threads.</p>
 */
@Slf4j
@Component
public class TelemetryConsumer {

    private final TelemetryProcessingService processingService;

    private final Counter messagesReceivedCounter;
    private final Counter messagesProcessedCounter;
    private final Counter messagesSkippedCounter;
    private final Timer   processingLatencyTimer;

    public TelemetryConsumer(
        TelemetryProcessingService processingService,
        MeterRegistry meterRegistry
    ) {
        this.processingService = processingService;

        this.messagesReceivedCounter  = Counter.builder("kafka.consumer.telemetry.received")
            .description("Total telemetry messages received from Kafka")
            .register(meterRegistry);
        this.messagesProcessedCounter = Counter.builder("kafka.consumer.telemetry.processed")
            .description("Total telemetry messages successfully processed")
            .register(meterRegistry);
        this.messagesSkippedCounter   = Counter.builder("kafka.consumer.telemetry.skipped")
            .description("Messages skipped due to validation issues")
            .register(meterRegistry);
        this.processingLatencyTimer   = Timer.builder("kafka.consumer.telemetry.latency")
            .description("End-to-end processing latency per telemetry message")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    /**
     * Processes a single telemetry event.
     *
     * <p>Uses the {@code telemetryListenerContainerFactory} defined in
     * {@code KafkaConsumerConfig} — which sets manual-ack mode and the retry/DLT
     * error handler.</p>
     *
     * <p>The {@code groupId} is set explicitly so multiple consumer groups
     * (e.g. AI agent services) can independently consume the same topic
     * without interfering with each other's offsets.</p>
     */
    @KafkaListener(
        topics            = "${voltvanguard.kafka.topics.telemetry-raw}",
        groupId           = "${spring.kafka.consumer.group-id}",
        containerFactory  = "telemetryListenerContainerFactory"
    )
    public void consume(
        ConsumerRecord<String, VehicleTelemetryEvent> record,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET)             long offset
    ) {
        Instant start = Instant.now();
        messagesReceivedCounter.increment();

        VehicleTelemetryEvent event = record.value();

        // Guard: malformed deserialized messages
        if (event == null || event.getVehicleId() == null) {
            log.warn("Skipping null/incomplete telemetry message at partition={} offset={}", partition, offset);
            messagesSkippedCounter.increment();
            acknowledgment.acknowledge();
            return;
        }

        log.debug("Consuming telemetry: vehicle={} battery={}% partition={} offset={}",
            event.getVehicleId(), event.getBatteryPercent(), partition, offset);

        // Measure end-to-end consumer lag: time from capture to processing
        if (event.getCapturedAt() != null) {
            long lagMs = Duration.between(event.getCapturedAt(), start).toMillis();
            if (lagMs > 5_000) {
                log.warn("High consumer lag: vehicle={} lag={}ms", event.getVehicleId(), lagMs);
            }
        }

        // Delegate to processing service — all business logic lives there
        processingService.process(event);

        // Commit offset only after successful processing
        acknowledgment.acknowledge();

        messagesProcessedCounter.increment();
        processingLatencyTimer.record(Duration.between(start, Instant.now()));
    }
}

package com.voltvanguard.core.kafka.producer;

import com.voltvanguard.core.kafka.event.VehicleAlertEvent;
import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Async Kafka producer for telemetry and alert events.
 *
 * <p>All sends are non-blocking: the caller receives a {@link CompletableFuture}
 * and is not stalled waiting for broker acknowledgement. Callbacks handle
 * success/failure logging and metrics.</p>
 *
 * <p>Partition key strategy: {@code vehicleId.toString()} ensures all events
 * for the same vehicle land on the same partition — preserving ordering per vehicle
 * while distributing load across partitions.</p>
 */
@Slf4j
@Component
public class TelemetryProducer {

    private final KafkaTemplate<String, VehicleTelemetryEvent> telemetryTemplate;
    private final KafkaTemplate<String, VehicleAlertEvent>     alertTemplate;

    private final String telemetryRawTopic;
    private final String vehicleAlertsTopic;

    // ── Micrometer counters ───────────────────────────────────────────────────
    private final Counter telemetrySentCounter;
    private final Counter telemetryFailedCounter;
    private final Counter alertSentCounter;

    public TelemetryProducer(
        @Qualifier("telemetryKafkaTemplate")
        KafkaTemplate<String, VehicleTelemetryEvent> telemetryTemplate,

        @Qualifier("alertKafkaTemplate")
        KafkaTemplate<String, VehicleAlertEvent> alertTemplate,

        @Value("${voltvanguard.kafka.topics.telemetry-raw}") String telemetryRawTopic,
        @Value("${voltvanguard.kafka.topics.vehicle-alerts}") String vehicleAlertsTopic,

        MeterRegistry meterRegistry
    ) {
        this.telemetryTemplate   = telemetryTemplate;
        this.alertTemplate       = alertTemplate;
        this.telemetryRawTopic   = telemetryRawTopic;
        this.vehicleAlertsTopic  = vehicleAlertsTopic;

        this.telemetrySentCounter  = Counter.builder("kafka.telemetry.sent")
            .description("Total telemetry events published to Kafka")
            .register(meterRegistry);
        this.telemetryFailedCounter = Counter.builder("kafka.telemetry.failed")
            .description("Total telemetry events that failed to publish")
            .register(meterRegistry);
        this.alertSentCounter = Counter.builder("kafka.alerts.sent")
            .description("Total alert events published to Kafka")
            .register(meterRegistry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Publishes a telemetry event asynchronously.
     *
     * <p>Key = vehicleId (ensures ordered delivery per vehicle).
     *
     * @param event the telemetry payload
     * @return future that resolves to the send metadata on success
     */
    public CompletableFuture<SendResult<String, VehicleTelemetryEvent>> sendTelemetry(
        VehicleTelemetryEvent event
    ) {
        String key = event.getVehicleId().toString();

        CompletableFuture<SendResult<String, VehicleTelemetryEvent>> future =
            telemetryTemplate.send(telemetryRawTopic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                telemetrySentCounter.increment();
                log.debug("Telemetry sent: vehicle={} partition={} offset={} battery={}%",
                    event.getVehicleId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getBatteryPercent());
            } else {
                telemetryFailedCounter.increment();
                log.error("Failed to send telemetry for vehicle={}: {}",
                    event.getVehicleId(), ex.getMessage(), ex);
            }
        });

        return future;
    }

    /**
     * Publishes a vehicle alert event asynchronously.
     *
     * <p>Key = vehicleId (co-partitioned with telemetry for stream joins).
     *
     * @param alert the alert payload
     */
    public CompletableFuture<SendResult<String, VehicleAlertEvent>> sendAlert(
        VehicleAlertEvent alert
    ) {
        String key = alert.getVehicleId().toString();

        CompletableFuture<SendResult<String, VehicleAlertEvent>> future =
            alertTemplate.send(vehicleAlertsTopic, key, alert);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                alertSentCounter.increment();
                log.info("Alert published: vehicle={} type={} severity={}",
                    alert.getVehicleId(), alert.getAlertType(), alert.getSeverity());
            } else {
                log.error("Failed to publish alert for vehicle={} type={}: {}",
                    alert.getVehicleId(), alert.getAlertType(), ex.getMessage(), ex);
            }
        });

        return future;
    }
}

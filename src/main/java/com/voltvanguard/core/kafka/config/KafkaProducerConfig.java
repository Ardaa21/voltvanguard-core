package com.voltvanguard.core.kafka.config;

import com.voltvanguard.core.kafka.event.VehicleAlertEvent;
import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer configuration.
 *
 * <p>Two typed {@link KafkaTemplate} beans are defined:
 * <ul>
 *   <li>{@code telemetryKafkaTemplate} — for {@link VehicleTelemetryEvent} messages</li>
 *   <li>{@code alertKafkaTemplate} — for {@link VehicleAlertEvent} messages</li>
 * </ul>
 *
 * <p>Key producer settings:
 * <ul>
 *   <li>{@code enable.idempotence=true} — prevents duplicate messages on retry</li>
 *   <li>{@code acks=all} — message is only acknowledged when all ISR replicas confirm</li>
 *   <li>{@code compression.type=snappy} — ~50% bandwidth reduction for JSON payloads</li>
 *   <li>{@code batch.size + linger.ms} — micro-batching for throughput without sacrificing latency</li>
 * </ul>
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Shared base config ────────────────────────────────────────────────────

    private Map<String, Object> baseProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,       bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,    StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,  JsonSerializer.class);

        // Reliability
        props.put(ProducerConfig.ACKS_CONFIG,                    "all");
        props.put(ProducerConfig.RETRIES_CONFIG,                 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,      true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Throughput
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,        "snappy");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,              32 * 1024);   // 32 KB
        props.put(ProducerConfig.LINGER_MS_CONFIG,               5);           // 5ms batch window

        // Timeouts
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,      30_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,     120_000);

        // Clean headers — don't leak Java class names to Kafka
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return props;
    }

    // ── Telemetry Producer ────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, VehicleTelemetryEvent> telemetryProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerProps());
    }

    @Bean
    public KafkaTemplate<String, VehicleTelemetryEvent> telemetryKafkaTemplate() {
        return new KafkaTemplate<>(telemetryProducerFactory());
    }

    // ── Alert Producer ────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, VehicleAlertEvent> alertProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerProps());
    }

    @Bean
    public KafkaTemplate<String, VehicleAlertEvent> alertKafkaTemplate() {
        return new KafkaTemplate<>(alertProducerFactory());
    }
}

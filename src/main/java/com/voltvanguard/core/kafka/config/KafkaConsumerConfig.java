package com.voltvanguard.core.kafka.config;

import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer configuration.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>Manual ACK</b> — consumer commits offset only after successful processing,
 *       ensuring no message is silently dropped if the processor throws an exception.</li>
 *   <li><b>Concurrency = 3</b> — one listener thread per partition; adjust to match
 *       {@code voltvanguard.kafka.partitions}.</li>
 *   <li><b>Exponential Backoff Retry</b> — 3 retries with backoff before routing
 *       to Dead Letter Topic; prevents thundering-herd on downstream failures.</li>
 *   <li><b>Dead Letter Topic</b> — poison messages go to {@code telemetry.raw.DLT}
 *       for offline analysis without blocking the live consumer.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${spring.kafka.listener.concurrency:3}")
    private int concurrency;

    @Value("${voltvanguard.kafka.topics.dead-letter}")
    private String deadLetterTopic;

    // ── Consumer Factory ──────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, VehicleTelemetryEvent> telemetryConsumerFactory() {
        JsonDeserializer<VehicleTelemetryEvent> deserializer =
            new JsonDeserializer<>(VehicleTelemetryEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("com.voltvanguard.core.kafka.event");
        deserializer.setUseTypeMapperForKey(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,       bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,  StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,       "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,      false);      // manual ACK
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,        500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,         1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,       500);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,      30_000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,   10_000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,    300_000);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,         "read_committed");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    // ── Listener Container Factory ────────────────────────────────────────────

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VehicleTelemetryEvent>
    telemetryListenerContainerFactory(
        ConsumerFactory<String, VehicleTelemetryEvent> telemetryConsumerFactory,
        KafkaTemplate<String, VehicleTelemetryEvent> telemetryKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, VehicleTelemetryEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(telemetryConsumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(buildErrorHandler(telemetryKafkaTemplate));

        return factory;
    }

    // ── Error Handler: Exponential Backoff + DLT ──────────────────────────────

    /**
     * Retry strategy:
     * <pre>
     *   Attempt 1 — immediate
     *   Attempt 2 — 2 seconds delay
     *   Attempt 3 — 4 seconds delay
     *   After 3 failures — route to Dead Letter Topic
     * </pre>
     *
     * <p>The {@link DeadLetterPublishingRecoverer} adds standard headers to the DLT message:
     * original topic, partition, offset, exception class and message.</p>
     */
    private DefaultErrorHandler buildErrorHandler(
        KafkaTemplate<String, VehicleTelemetryEvent> kafkaTemplate
    ) {
        // DLT recoverer: republish to <originalTopic>.DLT with error metadata headers
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // Exponential backoff: 2s initial, 2.0 multiplier, max 3 retries
        ExponentialBackOff backOff = new ExponentialBackOff(2_000L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Log retry attempts
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
            log.warn("Kafka retry attempt {} for topic={} partition={} offset={}: {}",
                deliveryAttempt,
                record.topic(),
                record.partition(),
                record.offset(),
                ex.getMessage())
        );

        // Don't retry on deserialization errors — they won't self-heal
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class
        );

        return errorHandler;
    }
}

package com.voltvanguard.core.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all Kafka topics as Spring-managed beans.
 *
 * <p>Spring Kafka's {@code KafkaAdmin} auto-creates these topics on startup
 * if they don't already exist. In production, prefer pre-provisioning topics
 * via Terraform or a Confluent Cloud config to control replication and cleanup policy.</p>
 *
 * <p>Topic naming follows the {@code domain.entity.event} convention.</p>
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${voltvanguard.kafka.topics.telemetry-raw}")
    private String telemetryRawTopic;

    @Value("${voltvanguard.kafka.topics.telemetry-processed}")
    private String telemetryProcessedTopic;

    @Value("${voltvanguard.kafka.topics.vehicle-alerts}")
    private String vehicleAlertsTopic;

    @Value("${voltvanguard.kafka.topics.dead-letter}")
    private String deadLetterTopic;

    @Value("${voltvanguard.kafka.partitions:3}")
    private int partitions;

    @Value("${voltvanguard.kafka.replication-factor:1}")
    private short replicationFactor;

    /**
     * Raw telemetry events from IoT Gateway / TelemetrySimulator.
     * High-volume ingestion: 3 partitions, compacted (keep latest per vehicle).
     */
    @Bean
    public NewTopic telemetryRawTopic() {
        return TopicBuilder.name(telemetryRawTopic)
            .partitions(partitions)
            .replicas(replicationFactor)
            .config("cleanup.policy", "delete")
            .config("retention.ms",  String.valueOf(24 * 60 * 60 * 1000L))  // 24h
            .config("compression.type", "snappy")
            .build();
    }

    /**
     * Processed telemetry — enriched and validated, published by TelemetryConsumer.
     * Consumed by AI agents for model inference.
     */
    @Bean
    public NewTopic telemetryProcessedTopic() {
        return TopicBuilder.name(telemetryProcessedTopic)
            .partitions(partitions)
            .replicas(replicationFactor)
            .config("cleanup.policy", "compact")          // Keep latest per vehicle VIN
            .config("compression.type", "snappy")
            .build();
    }

    /**
     * Vehicle alert events (battery critical, offline, temperature).
     * Low-volume, high-priority. Retained 7 days.
     */
    @Bean
    public NewTopic vehicleAlertsTopic() {
        return TopicBuilder.name(vehicleAlertsTopic)
            .partitions(1)                                 // Ordered delivery; 1 partition is fine
            .replicas(replicationFactor)
            .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000L))  // 7d
            .build();
    }

    /**
     * Dead Letter Topic: receives messages that failed after all retry attempts.
     * Retained 7 days for manual inspection and replay.
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(deadLetterTopic)
            .partitions(1)
            .replicas(replicationFactor)
            .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000L))
            .build();
    }
}

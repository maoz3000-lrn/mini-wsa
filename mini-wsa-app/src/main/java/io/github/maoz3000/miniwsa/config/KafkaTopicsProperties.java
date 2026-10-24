package io.github.maoz3000.miniwsa.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param topic    raw DLR events published by ingest and consumed by processing
 * @param dltTopic dead letters: records processing could not store
 */
@Validated
@ConfigurationProperties("miniwsa.kafka")
public record KafkaTopicsProperties(@NotBlank String topic, @NotBlank String dltTopic) {
}

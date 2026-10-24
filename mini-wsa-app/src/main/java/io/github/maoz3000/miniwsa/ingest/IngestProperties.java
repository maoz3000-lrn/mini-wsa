package io.github.maoz3000.miniwsa.ingest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param maxBodySize    largest accepted request body; larger requests get 413 before parsing
 * @param maxBatchSize   most events accepted in one request; more get 413
 * @param maxFutureSkew  how far ahead of the server clock an event timestamp may be before it is rejected
 * @param publishTimeout how long to wait for Kafka to acknowledge a batch before answering 503
 */
@Validated
@ConfigurationProperties("miniwsa.ingest")
public record IngestProperties(
        @NotNull DataSize maxBodySize,
        @Positive int maxBatchSize,
        @NotNull Duration maxFutureSkew,
        @NotNull Duration publishTimeout) {
}

package io.github.maoz3000.miniwsa.processing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param concurrency Kafka listener threads per instance (useful up to the number of partitions)
 * @param retry       backoff for transient failures; retries never give up, Kafka keeps the data meanwhile
 */
@Validated
@ConfigurationProperties("miniwsa.processing")
public record ProcessingProperties(@Positive int concurrency, @NotNull @Valid Retry retry) {

    public record Retry(@NotNull Duration initialInterval, @DecimalMin("1.0") double multiplier,
                        @NotNull Duration maxInterval) {
    }
}

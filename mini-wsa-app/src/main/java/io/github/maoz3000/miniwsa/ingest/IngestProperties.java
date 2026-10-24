package io.github.maoz3000.miniwsa.ingest;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param maxFutureSkew how far ahead of the server clock an event timestamp may be before it is rejected
 */
@Validated
@ConfigurationProperties("miniwsa.ingest")
public record IngestProperties(@NotNull Duration maxFutureSkew) {
}

package io.github.maoz3000.miniwsa.api;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param retryAfter value of the {@code Retry-After} header on 503 responses
 */
@Validated
@ConfigurationProperties("miniwsa.api")
public record ApiProperties(@NotNull Duration retryAfter) {
}

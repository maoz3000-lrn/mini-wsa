package io.github.maoz3000.miniwsa.storage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param accuracy     whether analytics reads remove not-yet-merged duplicates
 * @param queryTimeout server-side execution limit per analytics query
 */
@Validated
@ConfigurationProperties("miniwsa.analytics")
public record AnalyticsQueryProperties(@NotNull QueryAccuracy accuracy, @NotNull Duration queryTimeout) {
}

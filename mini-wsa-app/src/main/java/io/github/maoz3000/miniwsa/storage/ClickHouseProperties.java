package io.github.maoz3000.miniwsa.storage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param initSchema        create the ClickHouse table on startup if it does not exist
 * @param initRetries       attempts to reach ClickHouse for schema initialization before startup fails
 * @param initRetryInterval pause between those attempts
 */
@Validated
@ConfigurationProperties("miniwsa.clickhouse")
public record ClickHouseProperties(boolean initSchema, @Positive int initRetries, @NotNull Duration initRetryInterval) {

    public static ClickHouseProperties initOnce() {
        return new ClickHouseProperties(true, 1, Duration.ZERO);
    }
}

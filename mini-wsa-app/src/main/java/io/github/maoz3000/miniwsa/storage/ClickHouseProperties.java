package io.github.maoz3000.miniwsa.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param initSchema create the ClickHouse table on startup if it does not exist
 */
@ConfigurationProperties("miniwsa.clickhouse")
public record ClickHouseProperties(boolean initSchema) {
}

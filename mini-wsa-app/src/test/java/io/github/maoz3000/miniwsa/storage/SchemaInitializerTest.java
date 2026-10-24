package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.ContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SchemaInitializerTest {

    @Container
    private static final ClickHouseContainer CLICKHOUSE = ContainersConfig.clickHouseContainer();

    @Test
    void createsReplacingMergeTreeTableIdempotently() {
        DataSource dataSource = new SimpleDriverDataSource(new com.clickhouse.jdbc.Driver(),
                CLICKHOUSE.getJdbcUrl(), CLICKHOUSE.getUsername(), CLICKHOUSE.getPassword());
        SchemaInitializer initializer = new SchemaInitializer(dataSource, new ClickHouseProperties(true));

        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();

        assertThat(JdbcClient.create(dataSource)
                .sql("SELECT engine, sorting_key, partition_key FROM system.tables WHERE name = 'dlr_events'")
                .query((rs, n) -> rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3))
                .single())
                .isEqualTo("ReplacingMergeTree | config_id, timestamp, event_id | toYYYYMMDD(timestamp)");
    }
}

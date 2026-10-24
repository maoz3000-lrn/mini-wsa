package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.ContainersConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.clickhouse.ClickHouseContainer;

import javax.sql.DataSource;

/**
 * One ClickHouse container per test JVM for repository tests, with the schema applied.
 */
final class ClickHouseTestSupport {

    private static final ClickHouseContainer CLICKHOUSE = ContainersConfig.clickHouseContainer();
    private static DataSource dataSource;

    private ClickHouseTestSupport() {
    }

    static synchronized DataSource dataSource() {
        if (dataSource == null) {
            CLICKHOUSE.start();
            dataSource = new SimpleDriverDataSource(new com.clickhouse.jdbc.Driver(),
                    CLICKHOUSE.getJdbcUrl(), CLICKHOUSE.getUsername(), CLICKHOUSE.getPassword());
            new SchemaInitializer(dataSource, ClickHouseProperties.initOnce()).afterPropertiesSet();
        }
        return dataSource;
    }

    static JdbcTemplate truncatedTable() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        jdbc.execute("TRUNCATE TABLE dlr_events");
        return jdbc;
    }
}

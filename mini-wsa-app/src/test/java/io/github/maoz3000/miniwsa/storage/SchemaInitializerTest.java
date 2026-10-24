package io.github.maoz3000.miniwsa.storage;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaInitializerTest {

    private static final ClickHouseProperties RETRY_THREE_TIMES = new ClickHouseProperties(true, 3, Duration.ofMillis(1));

    @Test
    void createsReplacingMergeTreeTableIdempotently() {
        DataSource dataSource = ClickHouseTestSupport.dataSource();
        SchemaInitializer initializer = new SchemaInitializer(dataSource, ClickHouseProperties.initOnce());

        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();

        assertThat(JdbcClient.create(dataSource)
                .sql("SELECT engine, sorting_key, partition_key FROM system.tables WHERE name = 'dlr_events'")
                .query((rs, n) -> rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3))
                .single())
                .isEqualTo("ReplacingMergeTree | config_id, timestamp, event_id | toYYYYMMDD(timestamp)");
    }

    @Test
    void retriesWhileClickHouseIsUnreachable() {
        FlakyDataSource dataSource = new FlakyDataSource(2, new SQLException("down", new UnknownHostException("clickhouse")));

        new SchemaInitializer(dataSource, RETRY_THREE_TIMES).afterPropertiesSet();

        assertThat(dataSource.attempts).hasValue(3);
    }

    @Test
    void failsAfterLastAttempt() {
        FlakyDataSource dataSource = new FlakyDataSource(5, new SQLException("down", new UnknownHostException("clickhouse")));

        assertThatThrownBy(() -> new SchemaInitializer(dataSource, RETRY_THREE_TIMES).afterPropertiesSet())
                .hasRootCauseInstanceOf(UnknownHostException.class);
        assertThat(dataSource.attempts).hasValue(3);
    }

    @Test
    void failsImmediatelyOnOtherErrors() {
        FlakyDataSource dataSource = new FlakyDataSource(5, new SQLException("authentication failed"));

        assertThatThrownBy(() -> new SchemaInitializer(dataSource, RETRY_THREE_TIMES).afterPropertiesSet())
                .hasRootCauseMessage("authentication failed");
        assertThat(dataSource.attempts).hasValue(1);
    }

    /** Fails the first {@code failures} connection attempts, then connects to the test ClickHouse. */
    private static final class FlakyDataSource extends DelegatingDataSource {

        private final int failures;
        private final SQLException failure;
        private final AtomicInteger attempts = new AtomicInteger();

        FlakyDataSource(int failures, SQLException failure) {
            super(ClickHouseTestSupport.dataSource());
            this.failures = failures;
            this.failure = failure;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (attempts.incrementAndGet() <= failures) {
                throw failure;
            }
            return super.getConnection();
        }
    }
}

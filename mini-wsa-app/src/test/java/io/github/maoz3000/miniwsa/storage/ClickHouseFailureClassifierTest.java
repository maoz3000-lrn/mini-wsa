package io.github.maoz3000.miniwsa.storage;

import com.clickhouse.client.api.ServerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClickHouseFailureClassifierTest {

    @ParameterizedTest
    @ValueSource(ints = {6, 53, 117, 691})
    void badDataCodesArePermanent(int code) {
        Throwable failure = new DataAccessResourceFailureException("insert failed",
                new SQLException("bad data", "HY000", code));

        assertThat(ClickHouseFailureClassifier.classify("msg", failure)).isInstanceOf(PermanentStorageException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 159, 252, 241})
    void environmentCodesAreTransient(int code) {
        // UNKNOWN_TABLE, TIMEOUT_EXCEEDED, TOO_MANY_PARTS, MEMORY_LIMIT_EXCEEDED
        Throwable failure = new ServerException(code, "server error", 500, "query-id");

        assertThat(ClickHouseFailureClassifier.classify("msg", failure)).isInstanceOf(TransientStorageException.class);
    }

    @Test
    void failuresWithoutCodeAreTransient() {
        Throwable failure = new DataAccessResourceFailureException("connection refused", new IOException("refused"));

        assertThat(ClickHouseFailureClassifier.classify("msg", failure))
                .isInstanceOf(TransientStorageException.class)
                .hasCause(failure);
    }

    @Test
    void realBadDataRejectionFromServerIsPermanent() {
        JdbcTemplate jdbc = ClickHouseTestSupport.truncatedTable();

        Throwable failure = catchThrowable(() -> jdbc.execute(
                "INSERT INTO dlr_events (event_id, timestamp, received_at, config_id, rule_category) "
                        + "VALUES ('x', now64(), now64(), 1, 'NOT_A_CATEGORY')"));

        assertThat(ClickHouseFailureClassifier.classify("msg", failure)).isInstanceOf(PermanentStorageException.class);
    }
}

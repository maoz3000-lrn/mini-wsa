package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.storage.ClickHouseRowMapper.MappedBatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * Validates rows client-side, then writes all valid rows with one batched INSERT (one ClickHouse part).
 * Idempotency comes from ReplacingMergeTree: re-inserted events share the sorting key and collapse.
 */
@Slf4j
@Repository
@ConditionalOnRole(Role.PROCESSING)
public class ClickHouseEnrichedEventWriter implements EnrichedEventWriter {

    static final String INSERT_SQL = "INSERT INTO dlr_events (" + String.join(", ", ClickHouseRowMapper.COLUMNS)
            + ") VALUES (" + String.join(", ", Collections.nCopies(ClickHouseRowMapper.COLUMNS.size(), "?")) + ")";

    private final JdbcTemplate jdbcTemplate;

    public ClickHouseEnrichedEventWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SaveResult saveAll(List<EnrichedEvent> events) {
        MappedBatch batch = ClickHouseRowMapper.map(events);
        if (!batch.rows().isEmpty()) {
            insert(batch.rows());
        }
        log.debug("Stored {} events, rejected {}", batch.rows().size(), batch.rejected().size());
        return new SaveResult(batch.rejected());
    }

    private void insert(List<Object[]> rows) {
        try {
            jdbcTemplate.batchUpdate(INSERT_SQL, rows);
        } catch (DataAccessException e) {
            throw new TransientStorageException("ClickHouse insert of " + rows.size() + " rows failed", e);
        }
    }
}

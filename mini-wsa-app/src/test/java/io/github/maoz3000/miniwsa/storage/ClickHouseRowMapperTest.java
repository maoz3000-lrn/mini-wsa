package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.storage.ClickHouseRowMapper.MappedBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseRowMapperTest {

    @Test
    void mapsEventToRowInColumnOrder() {
        EnrichedEvent enriched = enriched(validEvent(), 75);

        MappedBatch batch = ClickHouseRowMapper.map(List.of(enriched));

        assertThat(batch.rejected()).isEmpty();
        assertThat(batch.rows()).singleElement().isEqualTo(new Object[]{
                "evt-00132", EVENT_TIME, EVENT_TIME.plusSeconds(2), 14227L, "pol_web1", "203.0.113.42",
                "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "950001", "SQL_INJECTION", "SQL Injection Attack Detected", "CRITICAL", "INJECTION", "DENY",
                "CN", "Beijing", 1024L, 256L, "SQL/Command Injection", 75});
        assertThat(batch.rows().getFirst()).hasSameSizeAs(ClickHouseRowMapper.COLUMNS.toArray());
    }

    @Test
    void mapsMissingOptionalFieldsToNull() {
        DlrEvent minimal = validEvent().toBuilder()
                .userAgent(null).geoLocation(null).requestSize(null).responseSize(null).build();

        Object[] row = ClickHouseRowMapper.map(List.of(enriched(minimal, 75))).rows().getFirst();

        assertThat(new Object[]{row[10], row[17], row[18], row[19], row[20]}).containsOnlyNulls();
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of((UnaryOperator<DlrEvent>) e -> e.toBuilder().timestamp(Instant.parse("1800-01-01T00:00:00Z")).build(),
                        75, "timestamp outside DateTime64 range"),
                Arguments.of((UnaryOperator<DlrEvent>) e -> e.toBuilder().configId(-1L).build(), 75,
                        "configId must be positive"),
                Arguments.of((UnaryOperator<DlrEvent>) e -> e.toBuilder().statusCode(70_000).build(), 75,
                        "statusCode out of UInt16 range"),
                Arguments.of((UnaryOperator<DlrEvent>) e -> e.toBuilder().path("/login\uD800").build(), 75,
                        "text contains invalid UTF-16 (unpaired surrogate)"));
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void rejectsOnlyInvalidEventsWithReason(UnaryOperator<DlrEvent> mutation, int score, String reason) {
        EnrichedEvent good = enriched(validEvent().toBuilder().eventId("good").build(), 75);
        EnrichedEvent bad = enriched(mutation.apply(validEvent()), score);

        MappedBatch batch = ClickHouseRowMapper.map(List.of(good, bad));

        assertThat(batch.rows()).singleElement().satisfies(row -> assertThat(row[0]).isEqualTo("good"));
        assertThat(batch.rejected()).containsExactly(new RejectedEvent(bad, reason));
    }

    private static EnrichedEvent enriched(DlrEvent event, int score) {
        return new EnrichedEvent(new IngestedEvent(event, EVENT_TIME.plusSeconds(2)), "SQL/Command Injection", score);
    }
}

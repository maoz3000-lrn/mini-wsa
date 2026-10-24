package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.EnrichedEventFixtures.enriched;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The worked example from the README: limit 5, four events share 10:00:05.120 across the page boundary.
 */
class PageCursorTest {

    private static final Instant TIE = Instant.parse("2026-05-20T10:00:05.120Z");

    @Test
    void cursorSkipsTiedEventsAlreadyReturned() {
        List<EnrichedEvent> firstPage = List.of(
                at("evt-120", "2026-05-20T10:00:07.000Z"), at("evt-119", "2026-05-20T10:00:06.500Z"),
                at("evt-118", TIE), at("evt-117", TIE), at("evt-116", TIE));

        assertThat(PageCursor.next(null, 0, firstPage, 5)).contains(new PageCursor(TIE, 3));
    }

    @Test
    void offsetAccumulatesWhileWholePagesShareTheBoundaryTimestamp() {
        List<EnrichedEvent> allTied = IntStream.range(0, 5).mapToObj(i -> at("tied-" + i, TIE)).toList();

        assertThat(PageCursor.next(TIE, 3, allTied, 5)).contains(new PageCursor(TIE, 8));
    }

    @Test
    void offsetResetsWhenBoundaryMovesToAnOlderTimestamp() {
        List<EnrichedEvent> page = Stream.of(
                        at("evt-115", TIE), at("evt-114", "2026-05-20T10:00:04.900Z"),
                        at("evt-113", "2026-05-20T10:00:03.000Z"), at("evt-112", "2026-05-20T10:00:02.000Z"),
                        at("evt-111", "2026-05-20T10:00:01.000Z"))
                .toList();

        assertThat(PageCursor.next(TIE, 3, page, 5))
                .contains(new PageCursor(Instant.parse("2026-05-20T10:00:01.000Z"), 1));
    }

    @Test
    void lastPageHasNoCursor() {
        assertThat(PageCursor.next(TIE, 3, List.of(at("evt-115", TIE)), 5)).isEqualTo(Optional.empty());
    }

    private static EnrichedEvent at(String eventId, String timestamp) {
        return at(eventId, Instant.parse(timestamp));
    }

    private static EnrichedEvent at(String eventId, Instant timestamp) {
        return enriched(validEvent().toBuilder().eventId(eventId).timestamp(timestamp).build(), 75);
    }
}

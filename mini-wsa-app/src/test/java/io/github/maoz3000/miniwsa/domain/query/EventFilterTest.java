package io.github.maoz3000.miniwsa.domain.query;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventFilterTest {

    private static final Instant T = Instant.parse("2026-05-20T00:00:00Z");

    @Test
    void acceptsEqualAndOrderedBounds() {
        assertThat(EventFilter.builder().from(T).to(T).build().from()).isEqualTo(T);
        assertThat(EventFilter.builder().from(T).to(T.plusSeconds(1)).build().to()).isEqualTo(T.plusSeconds(1));
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> EventFilter.builder().from(T.plusSeconds(1)).to(T).build())
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("from (2026-05-20T00:00:01Z) must not be after to (2026-05-20T00:00:00Z)");
    }

    @Test
    void unfilteredHasNoConstraints() {
        assertThat(EventFilter.unfiltered()).isEqualTo(new EventFilter(null, null, null, null, null));
    }
}

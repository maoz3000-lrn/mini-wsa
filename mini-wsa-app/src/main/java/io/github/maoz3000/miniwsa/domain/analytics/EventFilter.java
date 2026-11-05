package io.github.maoz3000.miniwsa.domain.analytics;

import io.github.maoz3000.miniwsa.domain.error.InvalidQueryException;
import io.github.maoz3000.miniwsa.domain.event.Action;
import io.github.maoz3000.miniwsa.domain.event.RuleCategory;
import lombok.Builder;

import java.time.Instant;

/**
 * Optional constraints on events; a {@code null} component means "no constraint".
 * {@code from} and {@code to} are both inclusive and refer to the event timestamp.
 */
@Builder(toBuilder = true)
public record EventFilter(Long configId, Instant from, Instant to, RuleCategory category, Action action) {

    public EventFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQueryException("from (" + from + ") must not be after to (" + to + ")");
        }
    }

    public static EventFilter unfiltered() {
        return builder().build();
    }
}

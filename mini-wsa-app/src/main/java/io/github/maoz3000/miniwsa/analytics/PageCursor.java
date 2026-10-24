package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Keyset pagination expressed with the existing {@code to} and {@code offset} parameters: the next page holds
 * events at or before the last returned timestamp ({@code to} is inclusive), skipping the events at exactly that
 * timestamp the client already has ({@code offset}). Newly arriving events cannot shift pages, and offsets stay
 * small however deep the client pages.
 */
public record PageCursor(Instant to, int offset) {

    /**
     * @param currentTo     {@code to} of the request that produced {@code page} ({@code null} if absent)
     * @param currentOffset {@code offset} of that request
     * @return where the next page starts, or empty when {@code page} is the last one
     */
    public static Optional<PageCursor> next(Instant currentTo, int currentOffset, List<EnrichedEvent> page, int limit) {
        if (page.size() < limit) {
            return Optional.empty();
        }
        Instant nextTo = page.getLast().event().timestamp();
        int tiesOnPage = (int) page.stream().filter(e -> e.event().timestamp().equals(nextTo)).count();
        // when the boundary timestamp did not move, the ties skipped so far are still behind the new boundary
        int carriedOver = nextTo.equals(currentTo) ? currentOffset : 0;
        return Optional.of(new PageCursor(nextTo, carriedOver + tiesOnPage));
    }
}

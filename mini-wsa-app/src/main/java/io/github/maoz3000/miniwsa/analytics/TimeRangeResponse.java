package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.domain.analytics.EventFilter;

import java.time.Instant;

/**
 * Time range of an analytics response, both bounds inclusive.
 */
public record TimeRangeResponse(Instant from, Instant to) {

    public static TimeRangeResponse from(EventFilter filter) {
        return new TimeRangeResponse(filter.from(), filter.to());
    }
}

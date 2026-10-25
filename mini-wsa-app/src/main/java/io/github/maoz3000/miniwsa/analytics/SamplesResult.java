package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;

import java.util.List;
import java.util.Optional;

/**
 * @param total  number of events matching the filters (independent of the page)
 * @param events the requested page, newest first
 * @param next   where the following page starts, empty on the last page
 */
public record SamplesResult(long total, List<EnrichedEvent> events, Optional<PageCursor> next) {
}

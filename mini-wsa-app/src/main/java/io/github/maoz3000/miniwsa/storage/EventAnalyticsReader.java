package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.query.AttackerStats;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PageRequest;
import io.github.maoz3000.miniwsa.domain.query.PathStats;
import io.github.maoz3000.miniwsa.domain.query.SummaryCounts;

import java.util.List;

/**
 * Read-only analytics over stored events. Every method honours all non-null constraints of the filter.
 *
 * @throws TransientStorageException from any method when the store cannot answer right now
 */
public interface EventAnalyticsReader {

    /** Total, per-category and per-action counts from a single read, so they are mutually consistent. */
    SummaryCounts summaryCounts(EventFilter filter);

    /** Client IPs by event count desc (ties by IP asc), at most {@code limit}. */
    List<AttackerStats> topAttackers(EventFilter filter, int limit);

    /** Paths by event count desc (ties by path asc), at most {@code limit}. */
    List<PathStats> topTargetedPaths(EventFilter filter, int limit);

    /** Matching events, newest first (event timestamp desc, ties by eventId desc). */
    List<EnrichedEvent> findSamples(EventFilter filter, PageRequest page);

    /** Number of matching events. */
    long countEvents(EventFilter filter);
}

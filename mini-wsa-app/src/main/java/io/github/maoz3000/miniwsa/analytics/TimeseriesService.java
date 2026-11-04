package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.BucketCount;
import io.github.maoz3000.miniwsa.domain.analytics.EventFilter;
import io.github.maoz3000.miniwsa.domain.error.InvalidQueryException;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.TimeInterval;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.Timeseries;
import io.github.maoz3000.miniwsa.storage.EventAnalyticsReader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Event counts per time bucket over a bounded range, zero-filled so a chart needs no gap handling.
 */
@Service
@ConditionalOnRole(Role.ANALYTICS)
public class TimeseriesService {

    /** Most buckets one response may hold: 24h at 1m, 5 days at 5m, 60 days at 1h. */
    public static final int MAX_BUCKETS = 1440;

    private final EventAnalyticsReader reader;
    private final Clock clock;

    public TimeseriesService(EventAnalyticsReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    /**
     * @param requested filter whose {@code from}/{@code to} may be {@code null}; defaults are applied
     * @throws InvalidQueryException when {@code from} ends up after {@code to} or the range exceeds {@link #MAX_BUCKETS}
     */
    public Timeseries timeseries(EventFilter requested, TimeInterval interval) {
        EventFilter filter = withDefaultBounds(requested, interval);
        long step = interval.duration().toMillis();
        // round both bounds down to a bucket start; floorDiv matches the SQL's positiveModulo (also before 1970)
        long first = Math.floorDiv(filter.from().toEpochMilli(), step) * step;
        long last = Math.floorDiv(filter.to().toEpochMilli(), step) * step;
        long totalBuckets = (last - first) / step + 1;
        if (totalBuckets > MAX_BUCKETS) {
            throw new InvalidQueryException("range of " + totalBuckets + " " + interval.label()
                    + " buckets exceeds the limit of " + MAX_BUCKETS);
        }
        // only non-empty buckets come back, keyed by start
        // e.g. 5m, from=10:02:30, to=10:14:00:
        //   [BucketCount(10:00, 4), BucketCount(10:10, 2)] -> {10:00=4, 10:10=2}
        Map<Instant, Long> countsByStart = reader.countsPerBucket(filter, interval).stream()
                .collect(Collectors.toMap(BucketCount::start, BucketCount::count));
        // every bucket start from first to last, e.g. totalBuckets=3: 0, 1, 2 -> 10:00, 10:05, 10:10
        List<BucketCount> buckets = LongStream.range(0, totalBuckets)
                .mapToObj(i -> Instant.ofEpochMilli(first + i * step))
                // missing buckets count 0, e.g. 10:00 -> 4, 10:05 -> 0, 10:10 -> 2
                .map(start -> new BucketCount(start, countsByStart.getOrDefault(start, 0L)))
                .toList();
        return new Timeseries(filter, interval, buckets);
    }

    // missing to -> now; missing from -> to minus the interval's default window
    // e.g. 5m, nothing given, now=2026-05-20T12:00Z -> from=2026-05-19T12:00Z, to=2026-05-20T12:00Z
    // building validates from <= to, e.g. from in the future with no to -> InvalidQueryException (400)
    private EventFilter withDefaultBounds(EventFilter requested, TimeInterval interval) {
        Instant to = Optional.ofNullable(requested.to()).orElseGet(clock::instant);
        Instant from = Optional.ofNullable(requested.from()).orElseGet(() -> to.minus(interval.defaultWindow()));
        return requested.toBuilder().from(from).to(to).build();
    }
}

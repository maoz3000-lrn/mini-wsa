package io.github.maoz3000.miniwsa.domain.query;

import java.util.List;

/**
 * Event counts for every bucket of the range, oldest first, zero when a bucket holds no events.
 *
 * @param filter the filter actually applied, with {@code from} and {@code to} always set
 */
public record Timeseries(EventFilter filter, TimeInterval interval, List<BucketCount> buckets) {
}

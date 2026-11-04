package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.analytics.TimeRangeResponse;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.Timeseries;

import java.time.Instant;
import java.util.List;

/**
 * Event counts per time bucket, oldest first, including empty buckets (count 0).
 *
 * @param configId  the requested configuration, or {@code null} when aggregated across all configurations
 * @param timeRange the range actually covered, with defaults applied for missing {@code from}/{@code to}
 * @param interval  bucket width as requested: {@code 1m}, {@code 5m} or {@code 1h}
 */
public record TimeseriesResponse(Long configId, TimeRangeResponse timeRange, String interval, List<Bucket> buckets) {

    /**
     * @param start inclusive start of the bucket, aligned to a multiple of the interval (UTC)
     */
    public record Bucket(Instant start, long count) {
    }

    public static TimeseriesResponse from(Timeseries series) {
        return new TimeseriesResponse(
                series.filter().configId(),
                TimeRangeResponse.from(series.filter()),
                series.interval().label(),
                series.buckets().stream().map(b -> new Bucket(b.start(), b.count())).toList());
    }
}

package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TimeRange;
import io.github.maoz3000.miniwsa.api.TimeseriesResponse;
import io.github.maoz3000.miniwsa.api.TimeseriesResponse.Bucket;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.Timeseries;

/**
 * Renders a time series for clients; the time range is the one actually covered.
 */
final class TimeseriesResponseMapper {

    private TimeseriesResponseMapper() {
    }

    static TimeseriesResponse toResponse(Timeseries series) {
        return new TimeseriesResponse(
                series.filter().configId(),
                new TimeRange(series.filter().from(), series.filter().to()),
                series.interval().label(),
                series.buckets().stream().map(b -> new Bucket(b.start(), b.count())).toList());
    }
}

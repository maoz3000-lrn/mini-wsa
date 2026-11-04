package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.BucketCount;
import io.github.maoz3000.miniwsa.domain.analytics.EventFilter;
import io.github.maoz3000.miniwsa.domain.error.InvalidQueryException;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.TimeInterval;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.Timeseries;
import io.github.maoz3000.miniwsa.storage.EventAnalyticsReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TimeseriesServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-20T12:00:00Z");
    private static final Instant T0 = Instant.parse("2026-05-20T10:00:00Z");

    private final EventAnalyticsReader reader = mock(EventAnalyticsReader.class);
    private final TimeseriesService service = new TimeseriesService(reader, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void zeroFillsBucketsAlignedToTheInterval() {
        EventFilter filter = EventFilter.builder().configId(14227L)
                .from(Instant.parse("2026-05-20T10:02:30Z")).to(Instant.parse("2026-05-20T10:14:00Z")).build();
        when(reader.countsPerBucket(filter, TimeInterval.FIVE_MINUTES)).thenReturn(List.of(
                new BucketCount(T0, 4), new BucketCount(T0.plus(Duration.ofMinutes(10)), 2)));

        assertThat(service.timeseries(filter, TimeInterval.FIVE_MINUTES)).isEqualTo(new Timeseries(
                filter, TimeInterval.FIVE_MINUTES, List.of(
                        new BucketCount(T0, 4),
                        new BucketCount(T0.plus(Duration.ofMinutes(5)), 0),
                        new BucketCount(T0.plus(Duration.ofMinutes(10)), 2))));
    }

    static Stream<Arguments> missingBounds() {
        return Stream.of(
                Arguments.of(null, null, NOW.minus(Duration.ofHours(24)), NOW),
                Arguments.of(T0, null, T0, NOW),
                Arguments.of(null, T0, T0.minus(Duration.ofHours(24)), T0));
    }

    @ParameterizedTest
    @MethodSource("missingBounds")
    void defaultsToNowAndTheIntervalWindow(Instant from, Instant to, Instant expectedFrom, Instant expectedTo) {
        EventFilter requested = EventFilter.builder().configId(7L).from(from).to(to).build();
        EventFilter resolved = requested.toBuilder().from(expectedFrom).to(expectedTo).build();

        assertThat(service.timeseries(requested, TimeInterval.FIVE_MINUTES).filter()).isEqualTo(resolved);
        verify(reader).countsPerBucket(resolved, TimeInterval.FIVE_MINUTES);
    }

    @ParameterizedTest
    @EnumSource(TimeInterval.class)
    void defaultWindowCoversTheIntervalsDefault(TimeInterval interval) {
        assertThat(service.timeseries(EventFilter.unfiltered(), interval).filter())
                .isEqualTo(EventFilter.builder().from(NOW.minus(interval.defaultWindow())).to(NOW).build());
    }

    @Test
    void rejectsFutureFromWhenToDefaultsToNow() {
        EventFilter requested = EventFilter.builder().from(NOW.plusSeconds(1)).build();

        assertThatThrownBy(() -> service.timeseries(requested, TimeInterval.ONE_MINUTE))
                .isInstanceOf(InvalidQueryException.class);
        verifyNoInteractions(reader);
    }

    @ParameterizedTest
    @EnumSource(TimeInterval.class)
    void acceptsExactlyTheMaximumNumberOfBuckets(TimeInterval interval) {
        EventFilter filter = EventFilter.builder()
                .from(T0).to(T0.plus(interval.duration().multipliedBy(TimeseriesService.MAX_BUCKETS - 1))).build();

        assertThat(service.timeseries(filter, interval).buckets()).hasSize(TimeseriesService.MAX_BUCKETS);
    }

    @ParameterizedTest
    @EnumSource(TimeInterval.class)
    void rejectsMoreThanTheMaximumNumberOfBucketsWithoutQuerying(TimeInterval interval) {
        EventFilter filter = EventFilter.builder()
                .from(T0).to(T0.plus(interval.duration().multipliedBy(TimeseriesService.MAX_BUCKETS))).build();

        assertThatThrownBy(() -> service.timeseries(filter, interval))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("range of 1441 " + interval.label() + " buckets exceeds the limit of 1440");
        verifyNoInteractions(reader);
    }

    @Test
    void propagatesStorageFailureUnwrapped() {
        when(reader.countsPerBucket(EventFilter.builder().from(T0).to(NOW).build(), TimeInterval.ONE_HOUR))
                .thenThrow(new TransientStorageException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.timeseries(EventFilter.builder().from(T0).build(), TimeInterval.ONE_HOUR))
                .isInstanceOf(TransientStorageException.class);
    }
}

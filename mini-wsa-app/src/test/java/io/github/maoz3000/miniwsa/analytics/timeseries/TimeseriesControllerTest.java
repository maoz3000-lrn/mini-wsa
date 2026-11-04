package io.github.maoz3000.miniwsa.analytics.timeseries;

import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.BucketCount;
import io.github.maoz3000.miniwsa.domain.analytics.EventFilter;
import io.github.maoz3000.miniwsa.domain.error.InvalidQueryException;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.TimeInterval;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.Timeseries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeseriesController.class)
@EnableConfigurationProperties(ApiProperties.class)
class TimeseriesControllerTest {

    private static final String PATH = "/v1/stats/timeseries";
    private static final Instant FROM = Instant.parse("2026-05-20T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-05-20T10:02:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeseriesService timeseriesService;

    @Test
    void passesFilterAndIntervalAndRendersBuckets() throws Exception {
        EventFilter filter = EventFilter.builder().configId(14227L).from(FROM).to(TO).build();
        when(timeseriesService.timeseries(filter, TimeInterval.ONE_MINUTE)).thenReturn(new Timeseries(
                filter, TimeInterval.ONE_MINUTE, List.of(
                        new BucketCount(FROM, 12), new BucketCount(FROM.plusSeconds(60), 5), new BucketCount(TO, 0))));

        mockMvc.perform(get(PATH).param("configId", "14227").param("from", FROM.toString()).param("to", TO.toString())
                        .param("interval", "1m"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"configId": 14227, "timeRange": {"from": "2026-05-20T10:00:00Z", "to": "2026-05-20T10:02:00Z"},
                         "interval": "1m",
                         "buckets": [{"start": "2026-05-20T10:00:00Z", "count": 12},
                                     {"start": "2026-05-20T10:01:00Z", "count": 5},
                                     {"start": "2026-05-20T10:02:00Z", "count": 0}]}
                        """, true));
    }

    @ParameterizedTest
    @CsvSource({"1m, ONE_MINUTE", "5m, FIVE_MINUTES", "1h, ONE_HOUR"})
    void leavesMissingBoundsToTheService(String label, TimeInterval interval) throws Exception {
        when(timeseriesService.timeseries(any(), any()))
                .thenReturn(new Timeseries(EventFilter.builder().from(FROM).to(TO).build(), interval, List.of()));

        mockMvc.perform(get(PATH).param("interval", label)).andExpect(status().isOk());

        verify(timeseriesService).timeseries(EventFilter.unfiltered(), interval);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2m", "1M", " 1m", "ONE_MINUTE"})
    void rejectsUnknownInterval(String interval) throws Exception {
        mockMvc.perform(get(PATH).param("interval", interval))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status": 400, "detail": "interval must be one of [1m, 5m, 1h], got '%s'"}
                        """.formatted(interval)));
        verifyNoInteractions(timeseriesService);
    }

    @Test
    void rejectsEmptyInterval() throws Exception {
        mockMvc.perform(get(PATH).param("interval", "")).andExpect(status().isBadRequest());
        verifyNoInteractions(timeseriesService);
    }

    @Test
    void rejectsMissingInterval() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isBadRequest());
        verifyNoInteractions(timeseriesService);
    }

    @Test
    void rejectsFromAfterTo() throws Exception {
        mockMvc.perform(get(PATH).param("interval", "1m").param("from", TO.toString()).param("to", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status": 400, "detail": "from (2026-05-20T10:02:00Z) must not be after to (2026-05-20T10:00:00Z)"}
                        """));
        verifyNoInteractions(timeseriesService);
    }

    @Test
    void returns400WhenTheServiceRejectsTheRange() throws Exception {
        when(timeseriesService.timeseries(any(), any()))
                .thenThrow(new InvalidQueryException("range of 1441 1m buckets exceeds the limit of 1440"));

        mockMvc.perform(get(PATH).param("interval", "1m"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status": 400, "detail": "range of 1441 1m buckets exceeds the limit of 1440"}
                        """));
    }

    @Test
    void returns503WhenStoreUnavailable() throws Exception {
        when(timeseriesService.timeseries(any(), any()))
                .thenThrow(new TransientStorageException("down", new RuntimeException()));

        mockMvc.perform(get(PATH).param("interval", "1h"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }
}

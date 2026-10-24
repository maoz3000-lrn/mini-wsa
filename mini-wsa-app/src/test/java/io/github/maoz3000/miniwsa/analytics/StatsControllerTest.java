package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.storage.TransientStorageException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@EnableConfigurationProperties(ApiProperties.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @Test
    void passesOptionalFiltersAndRendersSummary() throws Exception {
        EventFilter filter = EventFilter.builder().configId(14227L)
                .from(Instant.parse("2026-05-20T00:00:00Z")).to(Instant.parse("2026-05-20T23:59:59Z")).build();
        when(statsService.summary(filter)).thenReturn(new StatsSummaryResponse(14227L,
                new StatsSummaryResponse.TimeRange(filter.from(), filter.to()), 0, Map.of(), Map.of(), List.of(), List.of()));

        mockMvc.perform(get("/v1/stats/summary")
                        .param("configId", "14227").param("from", "2026-05-20T00:00:00Z").param("to", "2026-05-20T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"configId": 14227, "timeRange": {"from": "2026-05-20T00:00:00Z", "to": "2026-05-20T23:59:59Z"},
                         "totalEvents": 0, "byCategory": {}, "byAction": {}, "topAttackers": [], "topTargetedPaths": []}
                        """, true));
    }

    @Test
    void aggregatesAcrossConfigurationsWithoutParameters() throws Exception {
        when(statsService.summary(any())).thenReturn(new StatsSummaryResponse(null,
                new StatsSummaryResponse.TimeRange(null, null), 0, Map.of(), Map.of(), List.of(), List.of()));

        mockMvc.perform(get("/v1/stats/summary")).andExpect(status().isOk());

        verify(statsService).summary(EventFilter.unfiltered());
    }

    @Test
    void rejectsFromAfterTo() throws Exception {
        mockMvc.perform(get("/v1/stats/summary").param("from", "2026-05-21T00:00:00Z").param("to", "2026-05-20T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status": 400, "detail": "from (2026-05-21T00:00:00Z) must not be after to (2026-05-20T00:00:00Z)"}
                        """));
        verifyNoInteractions(statsService);
    }

    @Test
    void rejectsNonIsoTimestamp() throws Exception {
        mockMvc.perform(get("/v1/stats/summary").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns503WhenStoreUnavailable() throws Exception {
        when(statsService.summary(any())).thenThrow(new TransientStorageException("down", new RuntimeException()));

        mockMvc.perform(get("/v1/stats/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }
}

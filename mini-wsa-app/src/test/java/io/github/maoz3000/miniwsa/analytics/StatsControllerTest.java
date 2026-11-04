package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.api.JsonBodies;
import io.github.maoz3000.miniwsa.api.ProblemResponse;
import io.github.maoz3000.miniwsa.api.StatsSummaryJson;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.StatsSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@EnableConfigurationProperties(ApiProperties.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StatsService statsService;

    @Test
    void passesOptionalFiltersAndRendersSummary() throws Exception {
        EventFilter filter = EventFilter.builder().configId(14227L)
                .from(Instant.parse("2026-05-20T00:00:00Z")).to(Instant.parse("2026-05-20T23:59:59Z")).build();
        when(statsService.summary(filter))
                .thenReturn(new StatsSummary(filter, 0, Map.of(), Map.of(), List.of(), List.of()));

        MvcResult result = mockMvc.perform(get(ApiPaths.STATS_SUMMARY)
                        .param("configId", "14227").param("from", "2026-05-20T00:00:00Z").param("to", "2026-05-20T23:59:59Z"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, StatsSummaryJson.class)).isEqualTo(new StatsSummaryJson(
                filter.configId(), new StatsSummaryJson.TimeRange(filter.from(), filter.to()), 0,
                Map.of(), Map.of(), List.of(), List.of()));
    }

    @Test
    void aggregatesAcrossConfigurationsWithoutParameters() throws Exception {
        when(statsService.summary(any())).thenReturn(
                new StatsSummary(EventFilter.unfiltered(), 0, Map.of(), Map.of(), List.of(), List.of()));

        mockMvc.perform(get(ApiPaths.STATS_SUMMARY)).andExpect(status().isOk());

        verify(statsService).summary(EventFilter.unfiltered());
    }

    @Test
    void rejectsFromAfterTo() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiPaths.STATS_SUMMARY)
                        .param("from", "2026-05-21T00:00:00Z").param("to", "2026-05-20T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.BAD_REQUEST, "from (2026-05-21T00:00:00Z) must not be after to (2026-05-20T00:00:00Z)",
                ApiPaths.STATS_SUMMARY));
        verifyNoInteractions(statsService);
    }

    @Test
    void rejectsNonIsoTimestamp() throws Exception {
        mockMvc.perform(get(ApiPaths.STATS_SUMMARY).param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns503WhenStoreUnavailable() throws Exception {
        when(statsService.summary(any())).thenThrow(new TransientStorageException("down", new RuntimeException()));

        mockMvc.perform(get(ApiPaths.STATS_SUMMARY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }
}

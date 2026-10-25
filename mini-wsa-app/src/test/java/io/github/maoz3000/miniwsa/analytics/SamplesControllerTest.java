package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventJson;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SamplesController.class)
@EnableConfigurationProperties(ApiProperties.class)
class SamplesControllerTest {

    private static final EnrichedEvent SAMPLE = new EnrichedEvent(
            new IngestedEvent(validEvent(), EVENT_TIME.plusSeconds(2)), "SQL/Command Injection", 75);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SamplesService samplesService;

    @Test
    void returnsFlattenedEnrichedEventsWithTotal() throws Exception {
        when(samplesService.samples(any(), any())).thenReturn(new SamplesResult(42, List.of(SAMPLE), Optional.empty()));

        String expectedEvent = validEventJson().stripTrailing().replaceFirst("}$", """
                , "receivedAt": "2026-05-20T14:32:12Z", "attackType": "SQL/Command Injection", "threatScore": 75}""");
        mockMvc.perform(get("/v1/events/samples"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Link"))
                .andExpect(content().json("""
                        {"total": 42, "limit": 20, "offset": 0, "events": [%s]}
                        """.formatted(expectedEvent), true));
        verify(samplesService).samples(EventFilter.unfiltered(), new PageRequest(20, 0));
    }

    @Test
    void passesAllFiltersAndLinksToNextPageKeepingThem() throws Exception {
        Instant to = Instant.parse("2026-05-20T10:00:05.120Z");
        when(samplesService.samples(any(), any())).thenReturn(
                new SamplesResult(42, List.of(SAMPLE), Optional.of(new PageCursor(to, 3))));

        mockMvc.perform(get("/v1/events/samples?configId=14227&from=2026-05-20T00:00:00Z&category=INJECTION"
                        + "&action=DENY&limit=5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Link", "<http://localhost/v1/events/samples?configId=14227"
                        + "&from=2026-05-20T00:00:00Z&category=INJECTION&action=DENY&limit=5"
                        + "&to=2026-05-20T10:00:05.120Z&offset=3>; rel=\"next\""));
        verify(samplesService).samples(
                new EventFilter(14227L, Instant.parse("2026-05-20T00:00:00Z"), null, RuleCategory.INJECTION, Action.DENY),
                new PageRequest(5, 0));
    }

    @ParameterizedTest
    @CsvSource({"limit, 0", "limit, 101", "offset, -1", "category, SQLI", "action, BLOCK", "to, not-a-time"})
    void rejectsInvalidParameters(String name, String value) throws Exception {
        mockMvc.perform(get("/v1/events/samples").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
        verifyNoInteractions(samplesService);
    }
}

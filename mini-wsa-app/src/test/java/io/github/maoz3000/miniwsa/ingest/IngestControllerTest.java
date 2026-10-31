package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.api.ProblemResponse;
import io.github.maoz3000.miniwsa.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventJson;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
@Import({IngestWebConfig.class, IngestPayloadParser.class, IngestEventValidator.class, ClockConfig.class})
@EnableConfigurationProperties({ApiProperties.class, IngestProperties.class})
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IngestService ingestService;

    @Test
    void returns201WithAcceptedCount() throws Exception {
        when(ingestService.ingest(List.of(validEvent()))).thenReturn(new IngestResult(1, EVENT_TIME));

        mockMvc.perform(ingest(validEventJson()))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"accepted": 1, "receivedAt": "2026-05-20T14:32:10Z"}
                        """, true));
    }

    @Test
    void returns400ProblemWithItemizedErrorsWithoutIngesting() throws Exception {
        String invalidAction = validEventJson().replace("\"action\": \"DENY\"", "\"action\": \"NUKE\"");

        mockMvc.perform(ingest("[" + validEventJson() + "," + invalidAction + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("""
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "1 validation error(s); batch rejected",
                          "instance": "/v1/events/ingest",
                          "errors": [
                            {"index": 1, "field": "action", "message": "must be one of [DENY, ALERT, MONITOR]"}
                          ]
                        }
                        """, true));
        verify(ingestService, never()).ingest(anyList());
    }

    // a typed List<DlrEventRequest> body would fail on the first binding error and answer "not valid JSON";
    // binding each event separately must report every invalid event, binding and constraint errors alike
    @Test
    void returns400ListingEveryInvalidEventIncludingBindingErrors() throws Exception {
        List<JsonNode> batch = List.of(
                validEventNode(e -> e.put("action", "NUKE")),
                validEventNode(e -> { }),
                validEventNode(e -> e.put("statusCode", 700)),
                validEventNode(e -> e.put("timestamp", "yesterday")));

        MvcResult result = mockMvc.perform(ingest(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(ProblemResponse.read(objectMapper, result)).isEqualTo(new ProblemResponse(
                HttpStatus.BAD_REQUEST.getReasonPhrase(), HttpStatus.BAD_REQUEST.value(),
                "3 validation error(s); batch rejected", ApiPaths.INGEST, List.of(
                        new EventError(0, "action", "must be one of [DENY, ALERT, MONITOR]"),
                        new EventError(2, "statusCode", "must be less than or equal to 599"),
                        new EventError(3, "timestamp", IngestEventValidator.INVALID_TIMESTAMP))));
        verify(ingestService, never()).ingest(anyList());
    }

    @Test
    void returns400ProblemForRequestLevelError() throws Exception {
        mockMvc.perform(ingest("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status": 400, "errors": [{"message": "request body must contain at least one event"}]}
                        """));
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        mockMvc.perform(ingest("{\"eventId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status": 400, "detail": "request body is not valid JSON"}
                        """));
    }

    @Test
    void returns413WhenBatchTooLarge() throws Exception {
        String tooMany = "[" + String.join(",", Collections.nCopies(1001, "{}")) + "]";

        mockMvc.perform(ingest(tooMany))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().json("""
                        {"status": 413,
                         "detail": "batch of 1001 events exceeds the limit of 1000; split it into smaller requests"}
                        """));
    }

    // the batch limit is checked before any event is bound: an oversized batch is a 413
    // even when one of its events would fail binding
    @Test
    void returns413ForOversizedBatchEvenWhenAnEventFailsBinding() throws Exception {
        List<ObjectNode> tooMany = Stream.concat(
                        Collections.nCopies(1000, validEventNode(e -> { })).stream(),
                        Stream.of(validEventNode(e -> e.put("timestamp", "yesterday"))))
                .toList();

        MvcResult result = mockMvc.perform(ingest(objectMapper.writeValueAsString(tooMany)))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();

        assertThat(ProblemResponse.read(objectMapper, result)).isEqualTo(new ProblemResponse(
                HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase(), HttpStatus.CONTENT_TOO_LARGE.value(),
                "batch of 1001 events exceeds the limit of 1000; split it into smaller requests",
                ApiPaths.INGEST, null));
        verify(ingestService, never()).ingest(anyList());
    }

    @Test
    void returns413WhenBodyExceedsLimit() throws Exception {
        mockMvc.perform(ingest(" ".repeat(6 * 1024 * 1024)))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void returns503WithRetryAfterWhenKafkaUnavailable() throws Exception {
        when(ingestService.ingest(any())).thenThrow(new EventPublishException("no ack", new RuntimeException()));

        mockMvc.perform(ingest(validEventJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().json("""
                        {"status": 503, "detail": "Service temporarily unavailable; retry later"}
                        """));
    }

    private static org.springframework.test.web.servlet.RequestBuilder ingest(String body) {
        return post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

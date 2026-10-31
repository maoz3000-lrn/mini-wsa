package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.api.IngestResponseJson;
import io.github.maoz3000.miniwsa.api.JsonBodies;
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

    @Autowired
    private IngestProperties ingestProperties;

    @MockitoBean
    private IngestService ingestService;

    @Test
    void returns201WithAcceptedCount() throws Exception {
        when(ingestService.ingest(List.of(validEvent()))).thenReturn(new IngestResult(1, EVENT_TIME));

        MvcResult result = mockMvc.perform(ingest(validEventJson()))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, IngestResponseJson.class))
                .isEqualTo(new IngestResponseJson(1, EVENT_TIME));
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

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.BAD_REQUEST, "3 validation error(s); batch rejected", ApiPaths.INGEST, List.of(
                        new EventError(0, "action", "must be one of [DENY, ALERT, MONITOR]"),
                        new EventError(2, "statusCode", "must be less than or equal to 599"),
                        new EventError(3, "timestamp", IngestEventValidator.INVALID_TIMESTAMP))));
        verify(ingestService, never()).ingest(anyList());
    }

    @Test
    void returns400ProblemForRequestLevelError() throws Exception {
        MvcResult result = mockMvc.perform(ingest(objectMapper.writeValueAsString(List.of())))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.BAD_REQUEST, "1 validation error(s); batch rejected", ApiPaths.INGEST,
                List.of(EventError.ofRequest(IngestPayloadParser.EMPTY_BATCH))));
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(ingest("{\"eventId\": "))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.BAD_REQUEST, "request body is not valid JSON", ApiPaths.INGEST));
    }

    @Test
    void returns413WhenBatchTooLarge() throws Exception {
        String tooMany = objectMapper.writeValueAsString(Collections.nCopies(1001, objectMapper.createObjectNode()));

        MvcResult result = mockMvc.perform(ingest(tooMany))
                .andExpect(status().isContentTooLarge())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.CONTENT_TOO_LARGE,
                "batch of 1001 events exceeds the limit of 1000; split it into smaller requests", ApiPaths.INGEST));
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

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.CONTENT_TOO_LARGE,
                "batch of 1001 events exceeds the limit of 1000; split it into smaller requests", ApiPaths.INGEST));
        verify(ingestService, never()).ingest(anyList());
    }

    // the size filter answers before Spring MVC runs, so the problem it writes has no instance
    @Test
    void returns413WhenBodyExceedsLimit() throws Exception {
        MvcResult result = mockMvc.perform(ingest(" ".repeat(6 * 1024 * 1024)))
                .andExpect(status().isContentTooLarge())
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.CONTENT_TOO_LARGE,
                RequestSizeFilter.tooLargeMessage(ingestProperties.maxBodySize().toBytes()), null));
    }

    @Test
    void returns503WithRetryAfterWhenKafkaUnavailable() throws Exception {
        when(ingestService.ingest(any())).thenThrow(new EventPublishException("no ack", new RuntimeException()));

        MvcResult result = mockMvc.perform(ingest(validEventJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andReturn();

        assertThat(JsonBodies.read(objectMapper, result, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable; retry later", ApiPaths.INGEST));
    }

    private static org.springframework.test.web.servlet.RequestBuilder ingest(String body) {
        return post(ApiPaths.INGEST).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

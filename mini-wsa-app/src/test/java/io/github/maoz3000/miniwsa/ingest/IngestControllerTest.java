package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.ApiProperties;
import io.github.maoz3000.miniwsa.api.IngestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventJson;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
@Import(IngestWebConfig.class)
@EnableConfigurationProperties({ApiProperties.class, IngestProperties.class})
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestService ingestService;

    @Test
    void returns201WithAcceptedCount() throws Exception {
        when(ingestService.ingest(any())).thenReturn(new IngestResponse(1, EVENT_TIME));

        mockMvc.perform(ingest(validEventJson()))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"accepted": 1, "receivedAt": "2026-05-20T14:32:10Z"}
                        """, true));
    }

    @Test
    void returns400ProblemWithItemizedErrors() throws Exception {
        when(ingestService.ingest(any())).thenThrow(new EventValidationException(List.of(
                new EventError(0, "action", "must be one of [DENY, ALERT, MONITOR]"),
                EventError.ofRequest("whole request problem"))));

        mockMvc.perform(ingest(validEventJson()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("""
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "2 validation error(s); batch rejected",
                          "instance": "/v1/events/ingest",
                          "errors": [
                            {"index": 0, "field": "action", "message": "must be one of [DENY, ALERT, MONITOR]"},
                            {"message": "whole request problem"}
                          ]
                        }
                        """, true));
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
        when(ingestService.ingest(any())).thenThrow(new PayloadTooLargeException("too many events"));

        mockMvc.perform(ingest("[]"))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().json("""
                        {"status": 413, "detail": "too many events"}
                        """));
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

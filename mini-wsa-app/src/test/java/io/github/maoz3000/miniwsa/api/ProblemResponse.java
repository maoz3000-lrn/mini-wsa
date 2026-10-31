package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.ingest.EventError;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * An RFC 9457 problem response as the API writes it, for comparing whole error bodies in tests.
 *
 * @param errors the per-event problems of an ingest rejection, or {@code null} when the body has none
 */
public record ProblemResponse(String title, int status, String detail, String instance, List<EventError> errors) {

    /**
     * Reads the response body, failing on any property this record does not declare.
     */
    public static ProblemResponse read(ObjectMapper mapper, MvcResult result) {
        return mapper.readerFor(ProblemResponse.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(result.getResponse().getContentAsByteArray());
    }
}

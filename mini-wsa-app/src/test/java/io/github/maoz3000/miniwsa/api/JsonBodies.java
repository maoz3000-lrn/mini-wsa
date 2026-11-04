package io.github.maoz3000.miniwsa.api;

import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads HTTP response bodies into the test records that mirror their JSON shape, so tests compare whole objects.
 * Any property the record does not declare fails the read: an unexpected field is a contract change.
 */
public final class JsonBodies {

    private JsonBodies() {
    }

    public static <T> T read(ObjectMapper mapper, MvcResult result, Class<T> type) {
        return read(mapper, result.getResponse().getContentAsByteArray(), type);
    }

    public static <T> T read(ObjectMapper mapper, byte[] body, Class<T> type) {
        return mapper.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(body);
    }
}

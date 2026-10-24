package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Normalises the ingest body: a single JSON object becomes a batch of one, an array is a batch.
 */
@Component
@ConditionalOnRole(Role.INGEST)
public class IngestPayloadParser {

    static final String NOT_OBJECT_OR_ARRAY = "request body must be a JSON object or an array of objects";
    static final String EMPTY_BATCH = "request body must contain at least one event";
    static final String NOT_AN_OBJECT = "event must be a JSON object";

    public List<JsonNode> toBatch(JsonNode body) {
        if (body == null || !(body.isObject() || body.isArray())) {
            throw new EventValidationException(List.of(EventError.ofRequest(NOT_OBJECT_OR_ARRAY)));
        }
        List<JsonNode> batch = body.isObject() ? List.of(body) : body.valueStream().toList();
        if (batch.isEmpty()) {
            throw new EventValidationException(List.of(EventError.ofRequest(EMPTY_BATCH)));
        }
        List<EventError> nonObjects = IntStream.range(0, batch.size())
                .filter(i -> !batch.get(i).isObject())
                .mapToObj(i -> new EventError(i, null, NOT_AN_OBJECT))
                .toList();
        if (!nonObjects.isEmpty()) {
            throw new EventValidationException(nonObjects);
        }
        return batch;
    }
}

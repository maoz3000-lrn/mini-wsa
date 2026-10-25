package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.DlrEventRequest;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Binds and validates every event of a batch, collecting all problems before deciding (all-or-nothing).
 */
@Component
@ConditionalOnRole(Role.INGEST)
public class IngestEventValidator {

    static final String INVALID_TIMESTAMP = "must be an ISO-8601 timestamp, e.g. 2026-05-20T14:32:10Z";
    static final String INVALID_VALUE = "has an invalid value";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;
    private final IngestProperties properties;

    public IngestEventValidator(ObjectMapper objectMapper, Validator validator, Clock clock,
                                IngestProperties properties) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
        this.properties = properties;
    }

    public List<DlrEvent> validate(List<JsonNode> batch) {
        List<Bound> bound = IntStream.range(0, batch.size())
                .mapToObj(i -> bind(i, batch.get(i)))
                .toList();
        List<EventError> errors = bound.stream().flatMap(b -> b.errors().stream()).toList();
        if (!errors.isEmpty()) {
            throw new EventValidationException(errors);
        }
        return bound.stream().map(b -> b.request().toDomain()).toList();
    }

    private Bound bind(int index, JsonNode node) {
        try {
            DlrEventRequest request = objectMapper.treeToValue(node, DlrEventRequest.class);
            return new Bound(request,
                    Stream.concat(constraintErrors(index, request), skewErrors(index, request)).toList());
        } catch (JacksonException e) {
            return new Bound(null, List.of(new EventError(index, pathOf(e), bindingMessage(e))));
        }
    }

    private Stream<EventError> constraintErrors(int index, DlrEventRequest request) {
        return validator.validate(request).stream()
                .map(v -> new EventError(index, v.getPropertyPath().toString(), v.getMessage()))
                .sorted((a, b) -> a.field().compareTo(b.field()));
    }

    private Stream<EventError> skewErrors(int index, DlrEventRequest request) {
        Instant latestAllowed = clock.instant().plus(properties.maxFutureSkew());
        return request.timestamp() != null && request.timestamp().isAfter(latestAllowed)
                ? Stream.of(new EventError(index, "timestamp",
                        "must not be more than " + properties.maxFutureSkew() + " ahead of server time"))
                : Stream.empty();
    }

    private static String pathOf(JacksonException e) {
        return e.getPath().stream()
                .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private static String bindingMessage(JacksonException e) {
        Class<?> target = e instanceof MismatchedInputException mismatch ? mismatch.getTargetType() : null;
        if (target == null) {
            return INVALID_VALUE;
        }
        if (target.isEnum()) {
            return "must be one of " + Arrays.toString(target.getEnumConstants());
        }
        if (Instant.class.equals(target)) {
            return INVALID_TIMESTAMP;
        }
        return Number.class.isAssignableFrom(target) ? "must be a number" : INVALID_VALUE;
    }

    private record Bound(DlrEventRequest request, List<EventError> errors) {
    }
}

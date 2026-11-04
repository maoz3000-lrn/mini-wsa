package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.fixtures.PropertiesFixtures;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestEventValidatorTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final IngestEventValidator validator = new IngestEventValidator(
            MAPPER,
            Validation.buildDefaultValidatorFactory().getValidator(),
            Clock.fixed(EVENT_TIME, ZoneOffset.UTC),
            PropertiesFixtures.ingestProperties());

    @Test
    void bindsValidEvents() {
        assertThat(validator.validate(List.of(
                validEventNode(e -> { }), validEventNode(e -> e.put("eventId", "evt-2")))))
                .containsExactly(validEvent(), validEvent().toBuilder().eventId("evt-2").build());
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of("missing field", (Consumer<ObjectNode>) e -> e.remove("clientIp"),
                        new EventError(0, "clientIp", "must not be blank")),
                Arguments.of("unknown enum", (Consumer<ObjectNode>) e -> e.put("action", "BLOCK"),
                        new EventError(0, "action", "must be one of [DENY, ALERT, MONITOR]")),
                Arguments.of("nested unknown enum",
                        (Consumer<ObjectNode>) e -> ((ObjectNode) e.get("rule")).put("category", "SQLI"),
                        new EventError(0, "rule.category",
                                "must be one of [INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT]")),
                Arguments.of("bad timestamp", (Consumer<ObjectNode>) e -> e.put("timestamp", "20/05/2026 14:32"),
                        new EventError(0, "timestamp", IngestEventValidator.INVALID_TIMESTAMP)),
                Arguments.of("non-numeric config", (Consumer<ObjectNode>) e -> e.put("configId", "abc"),
                        new EventError(0, "configId", "must be a number")),
                Arguments.of("invalid ip", (Consumer<ObjectNode>) e -> e.put("clientIp", "10.0.0"),
                        new EventError(0, "clientIp", "must be a valid IPv4 or IPv6 address")),
                Arguments.of("too far in the future",
                        (Consumer<ObjectNode>) e -> e.put("timestamp", EVENT_TIME.plus(Duration.ofMinutes(6)).toString()),
                        new EventError(0, "timestamp", "must not be more than PT5M ahead of server time")),
                Arguments.of("before the storable range",
                        (Consumer<ObjectNode>) e -> e.put("timestamp", "1899-12-31T23:59:59Z"),
                        new EventError(0, "timestamp", "must not be before 1900-01-01T00:00:00Z")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEvents")
    void reportsFieldError(String scenario, Consumer<ObjectNode> mutation, EventError expected) {
        assertThatThrownBy(() -> validator.validate(List.of(validEventNode(mutation))))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.errors()).containsExactly(expected));
    }

    // historical events are accepted (backfills); only the storable range and the future skew are limits
    @ParameterizedTest
    @ValueSource(strings = {"1900-01-01T00:00:00Z", "2020-01-01T00:00:00Z", "2026-05-20T14:37:10Z"})
    void acceptsTimestampsWithinRange(String timestamp) {
        JsonNode event = validEventNode(e -> e.put("timestamp", timestamp));

        assertThat(validator.validate(List.of(event))).extracting(DlrEvent::timestamp)
                .containsExactly(Instant.parse(timestamp));
    }

    @Test
    void rejectsWholeBatchListingEveryInvalidEvent() {
        List<JsonNode> batch = List.of(
                validEventNode(e -> e.put("action", "BLOCK")),
                validEventNode(e -> { }),
                validEventNode(e -> e.remove("eventId")));

        assertThatThrownBy(() -> validator.validate(batch))
                .isInstanceOfSatisfying(EventValidationException.class, e -> assertThat(e.errors()).containsExactly(
                        new EventError(0, "action", "must be one of [DENY, ALERT, MONITOR]"),
                        new EventError(2, "eventId", "must not be blank")));
    }
}

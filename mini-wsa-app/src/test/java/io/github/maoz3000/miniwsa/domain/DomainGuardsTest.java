package io.github.maoz3000.miniwsa.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainGuardsTest {

    static Stream<Arguments> missingRequiredFields() {
        return Stream.of(
                Arguments.of("eventId", (Executable) () -> validEvent().toBuilder().eventId(null).build()),
                Arguments.of("timestamp", (Executable) () -> validEvent().toBuilder().timestamp(null).build()),
                Arguments.of("configId", (Executable) () -> validEvent().toBuilder().configId(null).build()),
                Arguments.of("clientIp", (Executable) () -> validEvent().toBuilder().clientIp(null).build()),
                Arguments.of("path", (Executable) () -> validEvent().toBuilder().path(null).build()),
                Arguments.of("rule", (Executable) () -> validEvent().toBuilder().rule(null).build()),
                Arguments.of("action", (Executable) () -> validEvent().toBuilder().action(null).build()),
                Arguments.of("severity", (Executable) () -> validEvent().rule().toBuilder().severity(null).build()),
                Arguments.of("category", (Executable) () -> validEvent().rule().toBuilder().category(null).build()),
                Arguments.of("event", (Executable) () -> new IngestedEvent(null, EVENT_TIME)),
                Arguments.of("receivedAt", (Executable) () -> new IngestedEvent(validEvent(), null)),
                Arguments.of("ingested", (Executable) () -> new EnrichedEvent(null, "Bot Activity", 10)),
                Arguments.of("attackType", (Executable) () -> new EnrichedEvent(ingested(), null, 10)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequiredFields")
    void rejectsMissingRequiredField(String field, Executable construction) {
        assertThatThrownBy(construction::execute).isInstanceOf(NullPointerException.class).hasMessage(field);
    }

    @Test
    void optionalFieldsMayBeMissing() {
        assertThatCode(() -> validEvent().toBuilder()
                .policyId(null).hostname(null).method(null).statusCode(null).userAgent(null)
                .geoLocation(null).requestSize(null).responseSize(null).build())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void rejectsThreatScoreOutOfRange(int score) {
        assertThatThrownBy(() -> new EnrichedEvent(ingested(), "Bot Activity", score))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("threatScore must be within [0, 100]: " + score);
    }

    private static IngestedEvent ingested() {
        return new IngestedEvent(validEvent(), EVENT_TIME);
    }
}

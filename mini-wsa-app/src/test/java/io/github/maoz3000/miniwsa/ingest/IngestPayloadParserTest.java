package io.github.maoz3000.miniwsa.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestPayloadParserTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final IngestPayloadParser parser = new IngestPayloadParser(
            new IngestProperties(DataSize.ofMegabytes(5), 2, Duration.ofMinutes(5)));

    @Test
    void wrapsSingleObjectInBatchOfOne() {
        JsonNode event = MAPPER.readTree("{\"eventId\":\"a\"}");

        assertThat(parser.toBatch(event)).containsExactly(event);
    }

    @Test
    void returnsArrayElementsInOrder() {
        JsonNode body = MAPPER.readTree("[{\"eventId\":\"a\"},{\"eventId\":\"b\"}]");

        assertThat(parser.toBatch(body)).containsExactly(body.get(0), body.get(1));
    }

    @Test
    void rejectsBatchAboveLimit() {
        JsonNode body = MAPPER.readTree("[{},{},{}]");

        assertThatThrownBy(() -> parser.toBatch(body))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("batch of 3 events exceeds the limit of 2; split it into smaller requests");
    }

    static Stream<Arguments> rejectedBodies() {
        return Stream.of(
                Arguments.of("[]", List.of(EventError.ofRequest(IngestPayloadParser.EMPTY_BATCH))),
                Arguments.of("\"text\"", List.of(EventError.ofRequest(IngestPayloadParser.NOT_OBJECT_OR_ARRAY))),
                Arguments.of("42", List.of(EventError.ofRequest(IngestPayloadParser.NOT_OBJECT_OR_ARRAY))),
                Arguments.of("[7, [1]]", List.of(
                        new EventError(0, null, IngestPayloadParser.NOT_AN_OBJECT),
                        new EventError(1, null, IngestPayloadParser.NOT_AN_OBJECT))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedBodies")
    void rejectsInvalidShapes(String body, List<EventError> expectedErrors) {
        assertThatThrownBy(() -> parser.toBatch(MAPPER.readTree(body)))
                .isInstanceOfSatisfying(EventValidationException.class,
                        e -> assertThat(e.errors()).isEqualTo(expectedErrors));
    }
}

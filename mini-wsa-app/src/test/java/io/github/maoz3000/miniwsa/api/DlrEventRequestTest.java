package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.api.DlrEventRequest.GeoLocationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validRequest;
import static org.assertj.core.api.Assertions.assertThat;

class DlrEventRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void assignmentExampleIsValid() {
        assertThat(VALIDATOR.validate(validRequest())).isEmpty();
    }

    @Test
    void mapsToTheDomainEvent() {
        assertThat(validRequest().toDomain()).isEqualTo(validEvent());
    }

    @Test
    void mapsMissingGeoLocationToNull() {
        assertThat(validRequest().toBuilder().geoLocation(null).build().toDomain())
                .isEqualTo(validEvent().toBuilder().geoLocation(null).build());
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("eventId", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().eventId(" ").build()),
                Arguments.of("timestamp", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().timestamp(null).build()),
                Arguments.of("configId", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().configId(0L).build()),
                Arguments.of("clientIp", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().clientIp("999.1.1.1").build()),
                Arguments.of("statusCode", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().statusCode(42).build()),
                Arguments.of("action", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().action(null).build()),
                Arguments.of("requestSize", (UnaryOperator<DlrEventRequest>) r -> r.toBuilder().requestSize(-1L).build()),
                Arguments.of("geoLocation.country", (UnaryOperator<DlrEventRequest>) r ->
                        r.toBuilder().geoLocation(new GeoLocationRequest("CHN", "Beijing")).build()),
                Arguments.of("rule.category", (UnaryOperator<DlrEventRequest>) r ->
                        r.toBuilder().rule(r.rule().toBuilder().category(null).build()).build()),
                Arguments.of("rule.severity", (UnaryOperator<DlrEventRequest>) r ->
                        r.toBuilder().rule(r.rule().toBuilder().severity(null).build()).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidField(String field, UnaryOperator<DlrEventRequest> mutation) {
        Set<String> invalidFields = VALIDATOR.validate(mutation.apply(validRequest())).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactly(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.42", "0.0.0.0", "2001:db8::1", "::1"})
    void acceptsIpLiterals(String ip) {
        assertThat(VALIDATOR.validate(validRequest().toBuilder().clientIp(ip).build())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.0.0.1", "1.2.3", "example.com", "2001:db8::zz", "1.2.3.4 "})
    void rejectsNonIpValues(String ip) {
        assertThat(VALIDATOR.validate(validRequest().toBuilder().clientIp(ip).build())).hasSize(1);
    }
}

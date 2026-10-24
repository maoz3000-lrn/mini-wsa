package io.github.maoz3000.miniwsa.domain;

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
import static org.assertj.core.api.Assertions.assertThat;

class DlrEventValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void assignmentExampleIsValid() {
        assertThat(VALIDATOR.validate(validEvent())).isEmpty();
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of("eventId", (UnaryOperator<DlrEvent>) e -> e.toBuilder().eventId(" ").build()),
                Arguments.of("timestamp", (UnaryOperator<DlrEvent>) e -> e.toBuilder().timestamp(null).build()),
                Arguments.of("configId", (UnaryOperator<DlrEvent>) e -> e.toBuilder().configId(0L).build()),
                Arguments.of("clientIp", (UnaryOperator<DlrEvent>) e -> e.toBuilder().clientIp("999.1.1.1").build()),
                Arguments.of("statusCode", (UnaryOperator<DlrEvent>) e -> e.toBuilder().statusCode(42).build()),
                Arguments.of("action", (UnaryOperator<DlrEvent>) e -> e.toBuilder().action(null).build()),
                Arguments.of("requestSize", (UnaryOperator<DlrEvent>) e -> e.toBuilder().requestSize(-1L).build()),
                Arguments.of("rule.category", (UnaryOperator<DlrEvent>) e ->
                        e.toBuilder().rule(e.rule().toBuilder().category(null).build()).build()),
                Arguments.of("rule.severity", (UnaryOperator<DlrEvent>) e ->
                        e.toBuilder().rule(e.rule().toBuilder().severity(null).build()).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEvents")
    void rejectsInvalidField(String field, UnaryOperator<DlrEvent> mutation) {
        Set<String> invalidFields = VALIDATOR.validate(mutation.apply(validEvent())).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(invalidFields).containsExactly(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.42", "0.0.0.0", "2001:db8::1", "::1"})
    void acceptsIpLiterals(String ip) {
        assertThat(VALIDATOR.validate(validEvent().toBuilder().clientIp(ip).build())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.0.0.1", "1.2.3", "example.com", "2001:db8::zz", "1.2.3.4 "})
    void rejectsNonIpValues(String ip) {
        assertThat(VALIDATOR.validate(validEvent().toBuilder().clientIp(ip).build())).hasSize(1);
    }
}

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

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
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
        assertThat(invalidFields(mutation.apply(validRequest()))).containsExactly(field);
    }

    static Stream<Arguments> formatRules() {
        return Stream.of(
                cases("eventId", (r, v) -> r.toBuilder().eventId(v).build(),
                        List.of("evt-00132", "550e8400-e29b-41d4-a716-446655440000", "a:b/c_d.e"),
                        List.of("evt 00132", "evt\t1", "évt-1", "evt-1\n")),
                cases("hostname", (r, v) -> r.toBuilder().hostname(v).build(),
                        List.of("www.example.com", "api.example.org:8443", "203.0.113.42", "localhost", "[2001:db8::1]:443"),
                        List.of("www.example.com/path", "exa mple.com", "host:", "host:123456", "ex@mple.com", "[nope]")),
                cases("path", (r, v) -> r.toBuilder().path(v).build(),
                        List.of("/", "/api/v1/login", "/search?q=1%27%20OR%201=1--", "/a\r\nSet-Cookie: x", "*",
                                "http://www.example.com/admin"),
                        List.of("index.html", "api/v1/login", " /login", "ftp://example.com/x", "**")),
                cases("method", (r, v) -> r.toBuilder().method(v).build(),
                        List.of("GET", "POST", "PROPFIND", "M-SEARCH", "get", "X_DEBUG"),
                        List.of("GET ", "GE T", "GET/1.1", "(GET)", "GÉT")),
                cases("geoLocation.country", (r, v) -> r.toBuilder().geoLocation(new GeoLocationRequest(v, "City")).build(),
                        List.of("US", "CN", "IL"),
                        List.of("us", "U", "USA", "U1", "")))
                .flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0} = \"{2}\" valid: {3}")
    @MethodSource("formatRules")
    void enforcesFieldFormat(String field, BiFunction<DlrEventRequest, String, DlrEventRequest> set, String value,
                             boolean valid) {
        assertThat(invalidFields(set.apply(validRequest(), value))).isEqualTo(valid ? Set.of() : Set.of(field));
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

    private static Stream<Arguments> cases(String field, BiFunction<DlrEventRequest, String, DlrEventRequest> set,
                                           List<String> valid, List<String> invalid) {
        return Stream.concat(valid.stream().map(v -> Arguments.of(field, set, v, true)),
                invalid.stream().map(v -> Arguments.of(field, set, v, false)));
    }

    private static Set<String> invalidFields(DlrEventRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}

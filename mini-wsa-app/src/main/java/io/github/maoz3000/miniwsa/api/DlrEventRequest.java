package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.GeoLocation;
import io.github.maoz3000.miniwsa.domain.Rule;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.RuleSeverity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;
import java.util.Optional;

/**
 * A security event (Data Log Record) as sent by the edge to the ingest API. The constraints are the API contract.
 */
@Builder(toBuilder = true)
public record DlrEventRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = PRINTABLE_ASCII, message = PRINTABLE_ASCII_MESSAGE) String eventId,
        @NotNull Instant timestamp,
        @NotNull @Positive Long configId,
        @NotBlank @Size(max = 64) String policyId,
        @NotBlank @IpAddress String clientIp,
        @NotBlank @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE) String hostname,
        @NotBlank @Size(max = 2048) @Pattern(regexp = REQUEST_TARGET, message = REQUEST_TARGET_MESSAGE) String path,
        @NotBlank @Size(max = 16) @Pattern(regexp = HTTP_TOKEN, message = HTTP_TOKEN_MESSAGE) String method,
        @NotNull @Min(100) @Max(599) Integer statusCode,
        @Size(max = 1024) String userAgent,
        @NotNull @Valid RuleRequest rule,
        @NotNull Action action,
        @Valid GeoLocationRequest geoLocation,
        @PositiveOrZero Long requestSize,
        @PositiveOrZero Long responseSize) {

    // the eventId is the Kafka key and the ClickHouse deduplication key: no whitespace or control characters
    static final String PRINTABLE_ASCII = "[\\x21-\\x7E]+";
    static final String PRINTABLE_ASCII_MESSAGE = "must be printable ASCII without spaces";

    // grouped by in the statistics and stored as LowCardinality: free text would split one site into many
    static final String HOST = "([A-Za-z0-9.-]+|\\[[0-9A-Fa-f:.]+])(:\\d{1,5})?";
    static final String HOST_MESSAGE = "must be a host name or IP literal, optionally with :port";

    // request-target forms of RFC 9112; raw attack payloads after the leading "/" are kept as sent
    static final String REQUEST_TARGET = "(?s)(/|https?://).*|\\*";
    static final String REQUEST_TARGET_MESSAGE = "must start with /, be *, or be an absolute http(s) URL";

    // an RFC 9110 token, not an enum: WebDAV and junk methods from attack traffic are real and must be logged
    static final String HTTP_TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    static final String HTTP_TOKEN_MESSAGE = "must be an HTTP method token";

    // grouped by in the statistics: "us" and "US" must not become two countries
    static final String COUNTRY_CODE = "[A-Z]{2}";
    static final String COUNTRY_CODE_MESSAGE = "must be an ISO 3166-1 alpha-2 code, e.g. US";

    /**
     * Call only on a request that passed validation: the domain event rejects missing required fields.
     */
    public DlrEvent toDomain() {
        return new DlrEvent(eventId, timestamp, configId, policyId, clientIp, hostname, path, method, statusCode,
                userAgent, rule.toDomain(), action,
                Optional.ofNullable(geoLocation).map(GeoLocationRequest::toDomain).orElse(null),
                requestSize, responseSize);
    }

    @Builder(toBuilder = true)
    public record RuleRequest(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String message,
            @NotNull RuleSeverity severity,
            @NotNull RuleCategory category) {

        Rule toDomain() {
            return new Rule(id, name, message, severity, category);
        }
    }

    public record GeoLocationRequest(
            @Pattern(regexp = COUNTRY_CODE, message = COUNTRY_CODE_MESSAGE) String country,
            @Size(max = 128) String city) {

        GeoLocation toDomain() {
            return new GeoLocation(country, city);
        }
    }
}

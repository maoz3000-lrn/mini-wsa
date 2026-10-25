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
        @NotBlank @Size(max = 128) String eventId,
        @NotNull Instant timestamp,
        @NotNull @Positive Long configId,
        @NotBlank @Size(max = 64) String policyId,
        @NotBlank @IpAddress String clientIp,
        @NotBlank @Size(max = 255) String hostname,
        @NotBlank @Size(max = 2048) String path,
        @NotBlank @Size(max = 16) String method,
        @NotNull @Min(100) @Max(599) Integer statusCode,
        @Size(max = 1024) String userAgent,
        @NotNull @Valid RuleRequest rule,
        @NotNull Action action,
        @Valid GeoLocationRequest geoLocation,
        @PositiveOrZero Long requestSize,
        @PositiveOrZero Long responseSize) {

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
            @Size(max = 2) String country,
            @Size(max = 128) String city) {

        GeoLocation toDomain() {
            return new GeoLocation(country, city);
        }
    }
}

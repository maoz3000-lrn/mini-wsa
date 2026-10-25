package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.GeoLocation;
import io.github.maoz3000.miniwsa.domain.Rule;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.RuleSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Optional;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A stored security event as returned to clients. Deliberately carries no validation: rows reflect the rules in
 * force when they were ingested, which may be looser than today's request contract.
 */
public record DlrEventResponse(
        @Schema(requiredMode = REQUIRED) String eventId,
        @Schema(requiredMode = REQUIRED) Instant timestamp,
        @Schema(requiredMode = REQUIRED) Long configId,
        String policyId,
        @Schema(requiredMode = REQUIRED) String clientIp,
        String hostname,
        @Schema(requiredMode = REQUIRED) String path,
        String method,
        Integer statusCode,
        String userAgent,
        @Schema(requiredMode = REQUIRED) RuleResponse rule,
        @Schema(requiredMode = REQUIRED) Action action,
        GeoLocationResponse geoLocation,
        Long requestSize,
        Long responseSize) {

    public static DlrEventResponse from(DlrEvent event) {
        return new DlrEventResponse(event.eventId(), event.timestamp(), event.configId(), event.policyId(),
                event.clientIp(), event.hostname(), event.path(), event.method(), event.statusCode(),
                event.userAgent(), RuleResponse.from(event.rule()), event.action(),
                Optional.ofNullable(event.geoLocation()).map(GeoLocationResponse::from).orElse(null),
                event.requestSize(), event.responseSize());
    }

    public record RuleResponse(
            String id,
            String name,
            String message,
            @Schema(requiredMode = REQUIRED) RuleSeverity severity,
            @Schema(requiredMode = REQUIRED) RuleCategory category) {

        static RuleResponse from(Rule rule) {
            return new RuleResponse(rule.id(), rule.name(), rule.message(), rule.severity(), rule.category());
        }
    }

    public record GeoLocationResponse(String country, String city) {

        static GeoLocationResponse from(GeoLocation geo) {
            return new GeoLocationResponse(geo.country(), geo.city());
        }
    }
}

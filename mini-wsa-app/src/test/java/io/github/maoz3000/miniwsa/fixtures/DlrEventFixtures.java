package io.github.maoz3000.miniwsa.fixtures;

import io.github.maoz3000.miniwsa.api.DlrEventRequest;
import io.github.maoz3000.miniwsa.api.DlrEventRequest.GeoLocationRequest;
import io.github.maoz3000.miniwsa.api.DlrEventRequest.RuleRequest;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.GeoLocation;
import io.github.maoz3000.miniwsa.domain.Rule;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.RuleSeverity;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Canonical test events, modelled on the example DLR from the assignment.
 */
public final class DlrEventFixtures {

    public static final Instant EVENT_TIME = Instant.parse("2026-05-20T14:32:10Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private DlrEventFixtures() {
    }

    public static DlrEvent validEvent() {
        return DlrEvent.builder()
                .eventId("evt-00132")
                .timestamp(EVENT_TIME)
                .configId(14227L)
                .policyId("pol_web1")
                .clientIp("203.0.113.42")
                .hostname("www.example.com")
                .path("/api/v1/login")
                .method("POST")
                .statusCode(403)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .rule(Rule.builder()
                        .id("950001")
                        .name("SQL_INJECTION")
                        .message("SQL Injection Attack Detected")
                        .severity(RuleSeverity.CRITICAL)
                        .category(RuleCategory.INJECTION)
                        .build())
                .action(Action.DENY)
                .geoLocation(new GeoLocation("CN", "Beijing"))
                .requestSize(1024L)
                .responseSize(256L)
                .build();
    }

    /**
     * The ingest request carrying {@link #validEvent()}.
     */
    public static DlrEventRequest validRequest() {
        return DlrEventRequest.builder()
                .eventId("evt-00132")
                .timestamp(EVENT_TIME)
                .configId(14227L)
                .policyId("pol_web1")
                .clientIp("203.0.113.42")
                .hostname("www.example.com")
                .path("/api/v1/login")
                .method("POST")
                .statusCode(403)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .rule(RuleRequest.builder()
                        .id("950001")
                        .name("SQL_INJECTION")
                        .message("SQL Injection Attack Detected")
                        .severity(RuleSeverity.CRITICAL)
                        .category(RuleCategory.INJECTION)
                        .build())
                .action(Action.DENY)
                .geoLocation(new GeoLocationRequest("CN", "Beijing"))
                .requestSize(1024L)
                .responseSize(256L)
                .build();
    }

    public static String validEventJson() {
        return """
                {
                  "eventId": "evt-00132",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 14227,
                  "policyId": "pol_web1",
                  "clientIp": "203.0.113.42",
                  "hostname": "www.example.com",
                  "path": "/api/v1/login",
                  "method": "POST",
                  "statusCode": 403,
                  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                  "rule": {
                    "id": "950001",
                    "name": "SQL_INJECTION",
                    "message": "SQL Injection Attack Detected",
                    "severity": "CRITICAL",
                    "category": "INJECTION"
                  },
                  "action": "DENY",
                  "geoLocation": { "country": "CN", "city": "Beijing" },
                  "requestSize": 1024,
                  "responseSize": 256
                }
                """;
    }

    /**
     * {@link #validEventJson()} as a JSON tree, changed by {@code mutation}. For events no typed request can
     * express, such as an unknown enum constant or an unparsable timestamp.
     */
    public static ObjectNode validEventNode(Consumer<ObjectNode> mutation) {
        ObjectNode node = (ObjectNode) MAPPER.readTree(validEventJson());
        mutation.accept(node);
        return node;
    }
}

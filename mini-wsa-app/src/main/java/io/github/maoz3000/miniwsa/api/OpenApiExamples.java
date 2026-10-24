package io.github.maoz3000.miniwsa.api;

/**
 * Request examples shown in Swagger UI ("Try it out" sends them as-is).
 */
public final class OpenApiExamples {

    public static final String SINGLE_EVENT = """
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
              "geoLocation": {"country": "CN", "city": "Beijing"},
              "requestSize": 1024,
              "responseSize": 256
            }""";

    public static final String BATCH = """
            [
              {"eventId": "evt-00133", "timestamp": "2026-05-20T14:32:11Z", "configId": 14227, "policyId": "pol_web1",
               "clientIp": "198.51.100.7", "hostname": "www.example.com", "path": "/search", "method": "GET",
               "statusCode": 200, "rule": {"id": "941100", "name": "XSS_ATTACK", "severity": "HIGH", "category": "XSS"},
               "action": "ALERT"},
              {"eventId": "evt-00134", "timestamp": "2026-05-20T14:32:12Z", "configId": 14227, "policyId": "pol_web1",
               "clientIp": "198.51.100.8", "hostname": "www.example.com", "path": "/products", "method": "GET",
               "statusCode": 200, "rule": {"id": "990001", "name": "BAD_BOT", "severity": "LOW", "category": "BOT"},
               "action": "MONITOR"}
            ]""";

    public static final String LINK_HEADER_DESCRIPTION = "Present unless this is the last page: "
            + "<...?to=...&offset=...>; rel=\"next\". Follow it to page without duplicates while new events arrive.";

    private OpenApiExamples() {
    }
}

package io.github.maoz3000.miniwsa.generator;

import io.github.maoz3000.miniwsa.generator.GeneratedEvent.GeoLocation;
import io.github.maoz3000.miniwsa.generator.GeneratedEvent.Rule;

import java.util.List;
import java.util.Map;

import static io.github.maoz3000.miniwsa.generator.Weighted.w;

/**
 * Coherent building blocks: each category has its own rules, targeted paths and severity profile, so the
 * generated data looks like what a WAF would actually emit.
 */
final class AttackCatalog {

    record CategoryProfile(List<RuleTemplate> rules, List<String> paths, Weighted<String> severity) {
    }

    record RuleTemplate(String id, String name, String message) {

        Rule toRule(String severity, String category) {
            return new Rule(id, name, message, severity, category);
        }
    }

    record Site(long configId, String hostname, String policyId) {
    }

    static final Weighted<String> CATEGORIES = Weighted.of(
            w("INJECTION", 20), w("XSS", 15), w("PROTOCOL_VIOLATION", 10), w("DATA_LEAKAGE", 5),
            w("BOT", 25), w("DOS", 10), w("RATE_LIMIT", 15));

    /** Categories typically seen as concentrated bursts from one source. */
    static final Weighted<String> WAVE_CATEGORIES = Weighted.of(
            w("INJECTION", 35), w("XSS", 15), w("BOT", 20), w("DOS", 15), w("RATE_LIMIT", 15));

    static final Map<String, CategoryProfile> PROFILES = Map.of(
            "INJECTION", new CategoryProfile(
                    List.of(new RuleTemplate("950001", "SQL_INJECTION", "SQL Injection Attack Detected"),
                            new RuleTemplate("932100", "COMMAND_INJECTION", "Remote Command Execution Detected")),
                    List.of("/api/v1/login", "/api/v1/search", "/products", "/admin/users"),
                    Weighted.of(w("CRITICAL", 60), w("HIGH", 40))),
            "XSS", new CategoryProfile(
                    List.of(new RuleTemplate("941100", "XSS_ATTACK", "XSS Attack Detected via libinjection")),
                    List.of("/search", "/comments", "/profile/edit"),
                    Weighted.of(w("HIGH", 50), w("MEDIUM", 50))),
            "PROTOCOL_VIOLATION", new CategoryProfile(
                    List.of(new RuleTemplate("920100", "INVALID_HTTP_REQUEST_LINE", "Invalid HTTP Request Line")),
                    List.of("/", "/index.html", "/api/v1/health"),
                    Weighted.of(w("MEDIUM", 40), w("LOW", 60))),
            "DATA_LEAKAGE", new CategoryProfile(
                    List.of(new RuleTemplate("950100", "SENSITIVE_DATA_EXPOSURE", "Sensitive Data Exposure Detected")),
                    List.of("/api/v1/users/export", "/admin/backup.zip", "/.env"),
                    Weighted.of(w("CRITICAL", 50), w("HIGH", 50))),
            "BOT", new CategoryProfile(
                    List.of(new RuleTemplate("990001", "BAD_BOT", "Known Bad Bot Detected")),
                    List.of("/", "/products", "/sitemap.xml", "/api/v1/prices"),
                    Weighted.of(w("MEDIUM", 30), w("LOW", 70))),
            "DOS", new CategoryProfile(
                    List.of(new RuleTemplate("970001", "HTTP_FLOOD", "HTTP Flood Detected")),
                    List.of("/", "/api/v1/search"),
                    Weighted.of(w("CRITICAL", 40), w("HIGH", 60))),
            "RATE_LIMIT", new CategoryProfile(
                    List.of(new RuleTemplate("980001", "RATE_LIMIT_EXCEEDED", "Rate Limit Exceeded")),
                    List.of("/api/v1/login", "/api/v1/cart"),
                    Weighted.of(w("MEDIUM", 40), w("LOW", 60))));

    static final Map<String, Weighted<String>> ACTIONS_BY_SEVERITY = Map.of(
            "CRITICAL", Weighted.of(w("DENY", 80), w("ALERT", 20)),
            "HIGH", Weighted.of(w("DENY", 60), w("ALERT", 30), w("MONITOR", 10)),
            "MEDIUM", Weighted.of(w("DENY", 30), w("ALERT", 40), w("MONITOR", 30)),
            "LOW", Weighted.of(w("DENY", 10), w("ALERT", 30), w("MONITOR", 60)));

    static final List<Site> SITES = List.of(
            new Site(14227, "www.example.com", "pol_web1"),
            new Site(14228, "shop.example.com", "pol_web1"),
            new Site(15001, "api.example.org", "pol_api1"));

    static final List<GeoLocation> GEO = List.of(
            new GeoLocation("CN", "Beijing"), new GeoLocation("RU", "Moscow"), new GeoLocation("US", "Ashburn"),
            new GeoLocation("BR", "Sao Paulo"), new GeoLocation("DE", "Frankfurt"), new GeoLocation("IN", "Mumbai"),
            new GeoLocation("NL", "Amsterdam"), new GeoLocation("KR", "Seoul"), new GeoLocation("VN", "Hanoi"),
            new GeoLocation("IL", "Tel Aviv"));

    static final List<String> BROWSER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0");

    static final List<String> TOOL_AGENTS = List.of(
            "sqlmap/1.8.4#stable (https://sqlmap.org)", "curl/8.5.0", "python-requests/2.32.3",
            "Go-http-client/1.1", "Mozilla/5.00 (Nikto/2.5.0)", "Mozilla/5.0 zgrab/0.x");

    static final List<String> POST_PATHS = List.of("/api/v1/login", "/comments", "/profile/edit", "/api/v1/cart");

    private AttackCatalog() {
    }
}

package io.github.maoz3000.miniwsa.generator;

import io.github.maoz3000.miniwsa.generator.AttackCatalog.CategoryProfile;
import io.github.maoz3000.miniwsa.generator.AttackCatalog.Site;
import io.github.maoz3000.miniwsa.generator.GeneratedEvent.GeoLocation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Creates individual events. All randomness comes from the injected generator, so a fixed seed reproduces
 * the same data.
 */
final class EventFactory {

    private static final double IPV6_SHARE = 0.05;

    private final RandomGenerator random;

    EventFactory(RandomGenerator random) {
        this.random = random;
    }

    /** One background event from a random source. */
    GeneratedEvent background(Instant timestamp) {
        String category = AttackCatalog.CATEGORIES.pick(random);
        CategoryProfile profile = AttackCatalog.PROFILES.get(category);
        String agent = random.nextDouble() < 0.7 ? pick(AttackCatalog.BROWSER_AGENTS) : pick(AttackCatalog.TOOL_AGENTS);
        return event(timestamp, pick(AttackCatalog.SITES), randomIp(), category, pick(profile.paths()), agent,
                pick(AttackCatalog.GEO));
    }

    /** One event of an attack wave: source, target and category are fixed by the wave. */
    GeneratedEvent waveEvent(Instant timestamp, Site site, String clientIp, String category, String path,
                             String userAgent, GeoLocation geo) {
        return event(timestamp, site, clientIp, category, path, userAgent, geo);
    }

    String randomIp() {
        return random.nextDouble() < IPV6_SHARE
                ? "2001:db8:%x::%x".formatted(random.nextInt(0x10000), random.nextInt(1, 0x10000))
                // coarse: skips first octets 10, 127, 172 and 192 so sources look like public addresses
                : "%d.%d.%d.%d".formatted(publicFirstOctet(), random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }

    <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private GeneratedEvent event(Instant timestamp, Site site, String clientIp, String category, String path,
                                 String userAgent, GeoLocation geo) {
        CategoryProfile profile = AttackCatalog.PROFILES.get(category);
        String severity = profile.severity().pick(random);
        String action = AttackCatalog.ACTIONS_BY_SEVERITY.get(severity).pick(random);
        return new GeneratedEvent(
                "evt-" + new UUID(random.nextLong(), random.nextLong()),
                timestamp,
                site.configId(),
                site.policyId(),
                clientIp,
                site.hostname(),
                path,
                AttackCatalog.POST_PATHS.contains(path) ? "POST" : "GET",
                statusCode(category, action),
                userAgent,
                pick(profile.rules()).toRule(severity, category),
                action,
                geo,
                random.nextLong(200, 5_000),
                "DENY".equals(action) ? random.nextLong(100, 600) : random.nextLong(500, 50_000));
    }

    private int statusCode(String category, String action) {
        return switch (action) {
            case "DENY" -> "RATE_LIMIT".equals(category) ? 429 : 403;
            case "ALERT" -> random.nextDouble() < 0.7 ? 200 : 403;
            default -> random.nextDouble() < 0.9 ? 200 : 404;
        };
    }

    private int publicFirstOctet() {
        int octet = random.nextInt(1, 224);
        return octet == 10 || octet == 127 || octet == 172 || octet == 192 ? octet + 1 : octet;
    }
}

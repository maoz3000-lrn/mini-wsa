package io.github.maoz3000.miniwsa.generator;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EventFactoryTest {

    private static final Instant T = Instant.parse("2026-05-20T10:00:00Z");

    @Test
    void sameSeedProducesSameEvents() {
        assertThat(sample(42)).isEqualTo(sample(42));
        assertThat(sample(42)).isNotEqualTo(sample(43));
    }

    @Test
    void producesEventsAcceptedByTheIngestSchema() {
        assertThat(sample(7)).allSatisfy(e -> {
            assertThat(e.eventId()).startsWith("evt-");
            assertThat(InetAddress.ofLiteral(e.clientIp())).isNotNull();
            assertThat(e.statusCode()).isBetween(100, 599);
            assertThat(e.action()).isIn("DENY", "ALERT", "MONITOR");
            assertThat(e.rule().severity()).isIn("CRITICAL", "HIGH", "MEDIUM", "LOW");
            assertThat(AttackCatalog.PROFILES.get(e.rule().category()).paths()).contains(e.path());
            assertThat(e.requestSize()).isPositive();
        });
    }

    @Test
    void coversEveryCategoryAndAction() {
        List<GeneratedEvent> events = sample(1);

        assertThat(events).extracting(e -> e.rule().category()).containsAll(AttackCatalog.PROFILES.keySet());
        assertThat(events).extracting(GeneratedEvent::action).contains("DENY", "ALERT", "MONITOR");
    }

    private static List<GeneratedEvent> sample(long seed) {
        EventFactory factory = new EventFactory(new SplittableRandom(seed));
        return IntStream.range(0, 2_000).mapToObj(i -> factory.background(T.plusSeconds(i))).toList();
    }
}

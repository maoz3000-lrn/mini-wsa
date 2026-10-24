package io.github.maoz3000.miniwsa.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficGeneratorTest {

    private static final Instant START = Instant.parse("2026-05-20T10:00:00Z");
    private static final TrafficPlan PLAN = new TrafficPlan(10_000, START, Duration.ofHours(1), 0.3, 20, 200, 42);

    @ParameterizedTest(name = "count={0}, waveRatio={1}")
    @CsvSource({"10000, 0.3", "1000, 0.0", "500, 1.0", "0, 0.3"})
    void generatesExactCountSortedWithinTimeRange(int count, double waveRatio) {
        List<GeneratedEvent> events = TrafficGenerator.generate(
                new TrafficPlan(count, START, Duration.ofHours(1), waveRatio, 20, 200, 1));

        assertThat(events).hasSize(count)
                .isSortedAccordingTo(Comparator.comparing(GeneratedEvent::timestamp))
                .allSatisfy(e -> assertThat(e.timestamp()).isBetween(START, START.plus(Duration.ofHours(1))));
    }

    @Test
    void isReproducibleForASeed() {
        assertThat(TrafficGenerator.generate(PLAN)).isEqualTo(TrafficGenerator.generate(PLAN));
    }

    @Test
    void wavesAreBurstsFromOneIpAtOnePathFastEnoughForTheRepeatOffenderBonus() {
        Map<String, List<GeneratedEvent>> byIp = TrafficGenerator.generate(PLAN).stream()
                .collect(Collectors.groupingBy(GeneratedEvent::clientIp));

        List<List<GeneratedEvent>> waves = byIp.values().stream().filter(events -> events.size() >= 20).toList();

        assertThat(waves).isNotEmpty().allSatisfy(wave -> {
            assertThat(wave).extracting(GeneratedEvent::path).containsOnly(wave.getFirst().path());
            assertThat(wave).extracting(e -> e.rule().category()).containsOnly(wave.getFirst().rule().category());
            // 7 consecutive events at most 3s apart always fit in the 10-minute window
            assertThat(Duration.between(wave.get(0).timestamp(), wave.get(6).timestamp()))
                    .isLessThanOrEqualTo(Duration.ofSeconds(18));
        });
        assertThat(waves.stream().mapToInt(List::size).sum()).isGreaterThanOrEqualTo(3_000);
    }

    @Test
    void rejectsInvalidPlans() {
        assertThatThrownBy(() -> new TrafficPlan(10, START, Duration.ofHours(1), 1.5, 20, 200, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrafficPlan(10, START, Duration.ofHours(1), 0.3, 50, 20, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

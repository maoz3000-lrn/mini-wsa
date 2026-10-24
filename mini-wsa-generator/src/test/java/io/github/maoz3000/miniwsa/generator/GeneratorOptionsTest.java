package io.github.maoz3000.miniwsa.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorOptionsTest {

    private static final Instant NOW = Instant.parse("2026-05-20T12:00:00Z");

    @Test
    void appliesDefaultsEndingNow() {
        GeneratorOptions options = GeneratorOptions.parse(new String[]{"--seed", "7"}, NOW);

        assertThat(options).isEqualTo(new GeneratorOptions(URI.create("http://localhost:8080"), 500, 4, Optional.empty(),
                new TrafficPlan(10_000, NOW.minus(Duration.ofHours(1)), Duration.ofHours(1), 0.3, 20, 200, 7)));
    }

    @Test
    void parsesEveryOption() {
        GeneratorOptions options = GeneratorOptions.parse(new String[]{
                "--url", "http://app:8080", "--count", "100", "--batch-size", "50", "--wave-ratio", "0.5",
                "--wave-size-min", "10", "--wave-size-max", "30", "--start", "2026-05-20T00:00:00Z",
                "--duration", "PT2H", "--seed", "3", "--concurrency", "8", "--out", "build/events"}, NOW);

        assertThat(options).isEqualTo(new GeneratorOptions(URI.create("http://app:8080"), 50, 8,
                Optional.of(Path.of("build/events")),
                new TrafficPlan(100, Instant.parse("2026-05-20T00:00:00Z"), Duration.ofHours(2), 0.5, 10, 30, 3)));
    }

    @ParameterizedTest
    @CsvSource({"45s, PT45S", "90m, PT1H30M", "2h, PT2H", "1d, PT24H", "PT15M, PT15M"})
    void parsesDurations(String text, Duration expected) {
        assertThat(GeneratorOptions.duration(text)).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownOrIncompleteOptions() {
        assertThatThrownBy(() -> GeneratorOptions.parse(new String[]{"--speed", "9"}, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown option --speed");
        assertThatThrownBy(() -> GeneratorOptions.parse(new String[]{"--count"}, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

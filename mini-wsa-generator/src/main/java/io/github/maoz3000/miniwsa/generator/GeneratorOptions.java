package io.github.maoz3000.miniwsa.generator;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Command line options ({@code --name value}).
 *
 * @param out directory to write batch files to; when absent, events are POSTed to {@code url}
 */
public record GeneratorOptions(URI url, int batchSize, int concurrency, Optional<Path> out, TrafficPlan plan) {

    static final String USAGE = """
            Usage: java -jar mini-wsa-generator.jar [options]
              --url <url>             ingest API base URL               (default http://localhost:8080)
              --count <n>             number of events                  (default 10000)
              --batch-size <n>        events per request / file         (default 500, API limit 1000)
              --wave-ratio <0..1>     share of events in attack waves   (default 0.3)
              --wave-size-min <n>     smallest wave                     (default 20)
              --wave-size-max <n>     largest wave                      (default 200)
              --start <ISO-8601>      earliest event timestamp          (default now - duration)
              --duration <duration>   event-time span, e.g. 1h, 30m, PT2H (default 1h)
              --seed <n>              random seed for reproducible data (default random)
              --concurrency <n>       parallel POST requests            (default 4)
              --out <dir>             write batch-NNNNN.json files instead of POSTing
            """;

    private static final Set<String> KNOWN = Set.of("url", "count", "batch-size", "wave-ratio", "wave-size-min",
            "wave-size-max", "start", "duration", "seed", "concurrency", "out");

    public static GeneratorOptions parse(String[] args, Instant now) {
        Map<String, String> values = pairs(args);
        Duration duration = duration(values.getOrDefault("duration", "1h"));
        Instant start = Optional.ofNullable(values.get("start")).map(Instant::parse).orElse(now.minus(duration));
        TrafficPlan plan = new TrafficPlan(
                Integer.parseInt(values.getOrDefault("count", "10000")),
                start,
                duration,
                Double.parseDouble(values.getOrDefault("wave-ratio", "0.3")),
                Integer.parseInt(values.getOrDefault("wave-size-min", "20")),
                Integer.parseInt(values.getOrDefault("wave-size-max", "200")),
                Optional.ofNullable(values.get("seed")).map(Long::parseLong)
                        .orElseGet(() -> ThreadLocalRandom.current().nextLong()));
        int batchSize = Integer.parseInt(values.getOrDefault("batch-size", "500"));
        int concurrency = Integer.parseInt(values.getOrDefault("concurrency", "4"));
        if (batchSize < 1 || concurrency < 1) {
            throw new IllegalArgumentException("batch-size and concurrency must be positive");
        }
        return new GeneratorOptions(URI.create(values.getOrDefault("url", "http://localhost:8080")), batchSize,
                concurrency, Optional.ofNullable(values.get("out")).map(Path::of), plan);
    }

    private static Map<String, String> pairs(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("options must be given as --name value pairs");
        }
        Map<String, String> values = new HashMap<>();
        IntStream.iterate(0, i -> i < args.length, i -> i + 2).forEach(i -> {
            String name = args[i].replaceFirst("^--", "");
            if (!args[i].startsWith("--") || !KNOWN.contains(name)) {
                throw new IllegalArgumentException("unknown option " + args[i]);
            }
            values.put(name, args[i + 1]);
        });
        return values;
    }

    /** Accepts ISO-8601 ({@code PT1H}) or a number with an s/m/h/d suffix ({@code 90m}). */
    static Duration duration(String text) {
        if (text.startsWith("P") || text.startsWith("p")) {
            return Duration.parse(text);
        }
        long amount = Long.parseLong(text.substring(0, text.length() - 1));
        return switch (text.charAt(text.length() - 1)) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("unsupported duration " + text);
        };
    }
}

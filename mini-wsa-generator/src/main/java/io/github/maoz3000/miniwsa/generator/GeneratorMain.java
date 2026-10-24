package io.github.maoz3000.miniwsa.generator;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Generates realistic security events with attack waves and sends them to the ingest API or writes them to files.
 */
@Slf4j
public final class GeneratorMain {

    private GeneratorMain() {
    }

    public static void main(String[] args) {
        if (List.of(args).contains("--help")) {
            log.info("\n{}", GeneratorOptions.USAGE);
            return;
        }
        GeneratorOptions options;
        try {
            options = GeneratorOptions.parse(args, Instant.now());
        } catch (IllegalArgumentException e) {
            log.error("{}\n{}", e.getMessage(), GeneratorOptions.USAGE);
            System.exit(2);
            return;
        }
        System.exit(run(options) ? 0 : 1);
    }

    static boolean run(GeneratorOptions options) {
        ObjectMapper mapper = JsonMapper.builder().build();
        List<GeneratedEvent> events = TrafficGenerator.generate(options.plan());
        List<List<GeneratedEvent>> batches = Batches.of(events, options.batchSize());
        log.info("Generated {} events ({}% in attack waves) between {} and {}, seed {}", events.size(),
                Math.round(options.plan().waveRatio() * 100), options.plan().start(), options.plan().end(),
                options.plan().seed());
        if (options.out().isPresent()) {
            Path directory = options.out().get();
            List<Path> files = new BatchFileWriter(mapper).write(directory, batches);
            log.info("Wrote {} batch files to {}", files.size(), directory.toAbsolutePath());
            return true;
        }
        try (HttpClient http = HttpClient.newHttpClient()) {
            IngestClient.Summary summary = new IngestClient(http, mapper, options.url(), options.concurrency())
                    .send(batches);
            log.info("Accepted {} of {} events by {}; failed batches: {}", summary.acceptedEvents(), events.size(),
                    options.url(), summary.failedBatches());
            return summary.failedBatches() == 0;
        }
    }
}

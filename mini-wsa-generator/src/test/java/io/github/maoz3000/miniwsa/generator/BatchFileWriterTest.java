package io.github.maoz3000.miniwsa.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchFileWriterTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void writesEachBatchAsIngestableJsonArray(@TempDir Path directory) {
        List<GeneratedEvent> events = TrafficGenerator.generate(
                new TrafficPlan(5, Instant.parse("2026-05-20T10:00:00Z"), Duration.ofMinutes(5), 0, 1, 1, 9));

        List<Path> files = new BatchFileWriter(MAPPER).write(directory, Batches.of(events, 2));

        assertThat(files).extracting(Path::getFileName).extracting(Path::toString)
                .containsExactly("batch-00001.json", "batch-00002.json", "batch-00003.json");
        assertThat(MAPPER.readValue(files.getFirst().toFile(), GeneratedEvent[].class))
                .containsExactly(events.get(0), events.get(1));
    }
}

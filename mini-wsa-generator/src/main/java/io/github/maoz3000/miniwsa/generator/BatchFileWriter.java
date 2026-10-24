package io.github.maoz3000.miniwsa.generator;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Writes each batch as a JSON array file that can be POSTed to the ingest API as-is.
 */
final class BatchFileWriter {

    private final ObjectMapper mapper;

    BatchFileWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<Path> write(Path directory, List<List<GeneratedEvent>> batches) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return IntStream.range(0, batches.size())
                .mapToObj(i -> {
                    Path file = directory.resolve("batch-%05d.json".formatted(i + 1));
                    mapper.writeValue(file.toFile(), batches.get(i));
                    return file;
                })
                .toList();
    }
}

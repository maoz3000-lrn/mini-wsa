package io.github.maoz3000.miniwsa.fixtures;

import io.github.maoz3000.miniwsa.config.KafkaTopicsProperties;
import io.github.maoz3000.miniwsa.ingest.IngestProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Properties matching the defaults in application.yml.
 */
public final class PropertiesFixtures {

    private PropertiesFixtures() {
    }

    public static IngestProperties ingestProperties() {
        return new IngestProperties(DataSize.ofMegabytes(5), 1000, Duration.ofMinutes(5), Duration.ofSeconds(5));
    }

    public static KafkaTopicsProperties kafkaTopics() {
        return new KafkaTopicsProperties("dlr-raw", "dlr-raw.DLT");
    }
}

package io.github.maoz3000.miniwsa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infrastructure for integration tests, using the same images as docker-compose.yml.
 * Containers are shared by every test context that imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.2");

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }
}

package io.github.maoz3000.miniwsa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infrastructure for integration tests, using the same images as docker-compose.yml.
 * Containers are shared by every test context that imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.2");
    public static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8.6.7-alpine");

    public static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return redisContainer();
    }
}

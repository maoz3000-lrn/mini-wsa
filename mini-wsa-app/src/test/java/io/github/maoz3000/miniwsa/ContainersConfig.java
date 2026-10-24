package io.github.maoz3000.miniwsa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infrastructure for integration tests, using the same images as docker-compose.yml.
 * Containers are JVM-wide singletons, shared by every test context that imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.2");
    public static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8.6.7-alpine");
    public static final DockerImageName CLICKHOUSE_IMAGE = DockerImageName.parse("clickhouse/clickhouse-server:26.3");

    private static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);
    private static final GenericContainer<?> REDIS = redisContainer();
    private static final ClickHouseContainer CLICKHOUSE = clickHouseContainer();

    public static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    }

    public static ClickHouseContainer clickHouseContainer() {
        return new ClickHouseContainer(CLICKHOUSE_IMAGE).withDatabaseName("miniwsa");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return KAFKA;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return REDIS;
    }

    @Bean
    @ServiceConnection
    ClickHouseContainer clickHouse() {
        return CLICKHOUSE;
    }

    @Bean
    NewTopic rawTopic() {
        return TopicBuilder.name("dlr-raw").partitions(3).build();
    }

    @Bean
    NewTopic deadLetterTopic() {
        return TopicBuilder.name("dlr-raw.DLT").partitions(3).build();
    }
}

package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.ContainersConfig;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/**
 * Kafka record in, ClickHouse row (or dead letter) out, through the real listener container.
 */
@SpringBootTest
@Import(ContainersConfig.class)
class ProcessingPipelineIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaConnectionDetails kafka;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanTable() {
        jdbc.execute("TRUNCATE TABLE dlr_events");
    }

    @Test
    void enrichesAndStoresEventsIncludingRepeatOffenderBonus() {
        String ip = "198.51.100." + (int) (Math.random() * 200);
        Instant start = Instant.parse("2026-05-20T10:00:00Z");
        // one partition => one consumer thread sees the wave in order; across partitions the bonus may be missed
        // for events right at the threshold (documented trade-off of partitioning by eventId)
        IntStream.range(0, 8).forEach(i -> send(eventFrom(ip, "wave-" + ip + "-" + i, start.plusSeconds(i)), 0));

        await().atMost(TIMEOUT).until(() -> count("client_ip = '" + ip + "'") == 8);

        // CRITICAL + DENY + /login = 75; the 7th and 8th events have 6 prior events => +15
        assertThat(jdbc.queryForList("SELECT toInt32(threat_score) FROM dlr_events FINAL WHERE client_ip = ? "
                + "ORDER BY timestamp", Integer.class, ip))
                .containsExactly(75, 75, 75, 75, 75, 75, 90, 90);
        assertThat(jdbc.queryForObject("SELECT any(attack_type) FROM dlr_events WHERE client_ip = ?", String.class, ip))
                .isEqualTo("SQL/Command Injection");
    }

    @Test
    void redeliveredEventIsStoredOnce() {
        IngestedEvent event = eventFrom("192.0.2.10", "dup-" + UUID.randomUUID(), Instant.parse("2026-05-20T11:00:00Z"));

        send(event);
        send(event);

        await().atMost(TIMEOUT).until(() -> count("event_id = '" + event.event().eventId() + "'") >= 1);
        assertThat(jdbc.queryForObject("SELECT count() FROM dlr_events FINAL WHERE event_id = ?", Long.class,
                event.event().eventId())).isEqualTo(1L);
    }

    @Test
    void deadLettersUnreadableAndUnstorableRecords() {
        String unreadableKey = "garbage-" + UUID.randomUUID();
        IngestedEvent invalid = eventFrom("192.0.2.20", "invalid-" + UUID.randomUUID(), Instant.parse("2026-05-20T12:00:00Z"));
        IngestedEvent badStatus = new IngestedEvent(invalid.event().toBuilder().statusCode(70_000).build(), invalid.receivedAt());

        sendRaw(unreadableKey, "not json".getBytes(StandardCharsets.UTF_8));
        send(badStatus);

        List<ConsumerRecord<String, byte[]>> letters = pollDeadLetters(Set.of(unreadableKey, badStatus.event().eventId()));
        assertThat(letters).extracting(ConsumerRecord::key, r -> new String(r.headers()
                        .lastHeader(KafkaDeadLetterSink.REASON_HEADER).value(), StandardCharsets.UTF_8))
                .containsExactlyInAnyOrder(
                        tuple(unreadableKey, "DESERIALIZATION"),
                        tuple(badStatus.event().eventId(), "ROW_VALIDATION"));
        assertThat(count("event_id = '" + badStatus.event().eventId() + "'")).isZero();
    }

    private long count(String condition) {
        return jdbc.queryForObject("SELECT count() FROM dlr_events WHERE " + condition, Long.class);
    }

    private static IngestedEvent eventFrom(String ip, String eventId, Instant timestamp) {
        return new IngestedEvent(validEvent().toBuilder().eventId(eventId).clientIp(ip).timestamp(timestamp).build(),
                timestamp.plusSeconds(1));
    }

    private void send(IngestedEvent event) {
        sendRaw(new ProducerRecord<>("dlr-raw", event.event().eventId(), objectMapper.writeValueAsBytes(event)));
    }

    private void send(IngestedEvent event, int partition) {
        sendRaw(new ProducerRecord<>("dlr-raw", partition, event.event().eventId(), objectMapper.writeValueAsBytes(event)));
    }

    private void sendRaw(String key, byte[] value) {
        sendRaw(new ProducerRecord<>("dlr-raw", key, value));
    }

    private void sendRaw(ProducerRecord<String, byte[]> record) {
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(
                Map.of("bootstrap.servers", String.join(",", kafka.getBootstrapServers())),
                new StringSerializer(), new ByteArraySerializer())) {
            producer.send(record);
        }
    }

    private List<ConsumerRecord<String, byte[]>> pollDeadLetters(Set<String> keys) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, byte[]> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of("dlr-raw.DLT"));
            List<ConsumerRecord<String, byte[]>> received = new ArrayList<>();
            await().atMost(TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(r -> {
                    if (keys.contains(r.key())) {
                        received.add(r);
                    }
                });
                return received.size() >= keys.size();
            });
            return received;
        }
    }
}

package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.ContainersConfig;
import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.IngestResponse;
import io.github.maoz3000.miniwsa.api.JsonBodies;
import io.github.maoz3000.miniwsa.api.ProblemResponse;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(ContainersConfig.class)
class IngestApiIntegrationTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private KafkaConnectionDetails kafka;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishesAcceptedEventsToKafkaKeyedByEventId() {
        String eventId = "evt-" + UUID.randomUUID();
        IngestResponse response = client.post().uri(ApiPaths.INGEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(validEventNode(e -> e.put("eventId", eventId))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(IngestResponse.class)
                .returnResult().getResponseBody();

        List<ConsumerRecord<String, String>> records = pollTopic("dlr-raw", eventId);

        assertThat(response.accepted()).isEqualTo(1);
        assertThat(records).singleElement().satisfies(r -> {
            assertThat(objectMapper.readValue(r.value(), IngestedEvent.class)).isEqualTo(
                    new IngestedEvent(validEvent().toBuilder().eventId(eventId).build(), response.receivedAt()));
        });
    }

    @Test
    void rejectsInvalidBatchWithoutPublishing() {
        byte[] body = client.post().uri(ApiPaths.INGEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(
                        List.of(validEventNode(e -> { }), validEventNode(e -> e.remove("clientIp")))))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().returnResult().getResponseBodyContent();

        assertThat(JsonBodies.read(objectMapper, body, ProblemResponse.class)).isEqualTo(ProblemResponse.of(
                HttpStatus.BAD_REQUEST, "1 validation error(s); batch rejected", ApiPaths.INGEST,
                List.of(new EventError(1, "clientIp", "must not be blank"))));
    }

    private List<ConsumerRecord<String, String>> pollTopic(String topic, String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(r -> {
                    if (key.equals(r.key())) {
                        received.add(r);
                    }
                });
                return !received.isEmpty();
            });
            return received;
        }
    }
}

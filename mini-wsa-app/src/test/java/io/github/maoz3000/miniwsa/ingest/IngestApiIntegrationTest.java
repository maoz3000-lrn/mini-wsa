package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.ContainersConfig;
import io.github.maoz3000.miniwsa.api.IngestResponse;
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
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEventJson;
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
        IngestResponse response = client.post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validEventJson().replace("evt-00132", eventId))
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
        client.post().uri("/v1/events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .body("[" + validEventJson() + ", {\"eventId\": \"broken\"}]")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].index").isEqualTo(1);
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

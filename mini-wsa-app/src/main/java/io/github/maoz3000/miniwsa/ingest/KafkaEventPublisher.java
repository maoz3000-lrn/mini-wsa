package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.KafkaTopicsProperties;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes one Kafka record per event, keyed by eventId, and returns only once the broker acknowledged all
 * of them ({@code acks=all}). A 201 to the client therefore means the batch is durably stored.
 */
@Component
@ConditionalOnRole(Role.INGEST)
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, IngestedEvent> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final IngestProperties properties;

    public KafkaEventPublisher(KafkaTemplate<String, IngestedEvent> kafkaTemplate, KafkaTopicsProperties topics,
                               IngestProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.properties = properties;
    }

    @Override
    public void publish(List<IngestedEvent> events) {
        CompletableFuture<?>[] sends = events.stream()
                .map(e -> kafkaTemplate.send(topics.topic(), e.event().eventId(), e))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sends).get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new EventPublishException("Kafka did not acknowledge " + events.size() + " events within "
                    + properties.publishTimeout(), e);
        } catch (ExecutionException e) {
            throw new EventPublishException("Kafka rejected the batch", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while waiting for Kafka acknowledgement", e);
        }
    }
}

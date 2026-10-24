package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.storage.EnrichedEventWriter;
import io.github.maoz3000.miniwsa.storage.PermanentStorageException;
import io.github.maoz3000.miniwsa.storage.SaveResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Processes one Kafka batch: enrich, store, dead-letter what cannot be stored, then commit offsets.
 * Every step is idempotent, so any failure before the commit simply re-runs the whole batch:
 * the Redis window ignores known eventIds and ClickHouse collapses re-inserted rows.
 * Transient failures are rethrown; the container's error handler retries the batch with backoff.
 */
@Slf4j
@Component
@ConditionalOnRole(Role.PROCESSING)
public class DlrBatchConsumer {

    private static final LogAccessor LOG_ACCESSOR = new LogAccessor(DlrBatchConsumer.class);

    private final EnrichmentService enrichmentService;
    private final EnrichedEventWriter writer;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;
    private final ProcessingMetrics metrics;

    public DlrBatchConsumer(EnrichmentService enrichmentService, EnrichedEventWriter writer,
                            DeadLetterPublisher deadLetterPublisher, ObjectMapper objectMapper,
                            ProcessingMetrics metrics) {
        this.enrichmentService = enrichmentService;
        this.writer = writer;
        this.deadLetterPublisher = deadLetterPublisher;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    // idIsGroup = false: the consumer group comes from spring.kafka.consumer.group-id, not from the listener id
    @KafkaListener(id = "dlr-processing", idIsGroup = false, topics = "${miniwsa.kafka.topic}", batch = "true",
            concurrency = "${miniwsa.processing.concurrency}")
    public void onBatch(List<ConsumerRecord<String, IngestedEvent>> records, Acknowledgment acknowledgment) {
        Map<IngestedEvent, ConsumerRecord<String, IngestedEvent>> sources = records.stream()
                .filter(r -> r.value() != null)
                .collect(Collectors.toMap(ConsumerRecord::value, Function.identity(), (a, b) -> a, IdentityHashMap::new));
        List<DeadLetter> unreadable = records.stream()
                .filter(r -> r.value() == null)
                .map(DlrBatchConsumer::unreadable)
                .toList();

        List<EnrichedEvent> enriched = enrichmentService.enrich(List.copyOf(sources.keySet()));
        List<DeadLetter> unstored = store(enriched, sources);

        List<DeadLetter> deadLetters = Stream.concat(unreadable.stream(), unstored.stream()).toList();
        deadLetterPublisher.publish(deadLetters);
        acknowledgment.acknowledge();
        metrics.recordDeadLetters(deadLetters);
        log.debug("Processed batch of {} records ({} dead-lettered)", records.size(), unreadable.size() + unstored.size());
    }

    private List<DeadLetter> store(List<EnrichedEvent> enriched,
                                   Map<IngestedEvent, ConsumerRecord<String, IngestedEvent>> sources) {
        if (enriched.isEmpty()) {
            return List.of();
        }
        try {
            SaveResult result = writer.saveAll(enriched);
            metrics.recordStored(stored(enriched, result));
            return result.rejected().stream()
                    .map(r -> deadLetter(sources.get(r.event().ingested()), DeadLetterReason.ROW_VALIDATION, r.reason()))
                    .toList();
        } catch (PermanentStorageException e) {
            log.error("Store rejected a batch of {} events; sending all of them to the dead-letter topic",
                    enriched.size(), e);
            return enriched.stream()
                    .map(ev -> deadLetter(sources.get(ev.ingested()), DeadLetterReason.STORAGE_REJECTED, e.getMessage()))
                    .toList();
        }
    }

    private static List<EnrichedEvent> stored(List<EnrichedEvent> enriched, SaveResult result) {
        Set<EnrichedEvent> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
        result.rejected().forEach(r -> rejected.add(r.event()));
        return enriched.stream().filter(e -> !rejected.contains(e)).toList();
    }

    private DeadLetter deadLetter(ConsumerRecord<String, IngestedEvent> source, DeadLetterReason reason, String error) {
        return new DeadLetter(source.key(), objectMapper.writeValueAsBytes(source.value()), reason, error,
                source.topic(), source.partition(), source.offset());
    }

    private static DeadLetter unreadable(ConsumerRecord<String, IngestedEvent> source) {
        Optional<DeserializationException> failure = Optional.ofNullable(SerializationUtils.getExceptionFromHeader(
                source, SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, LOG_ACCESSOR));
        return new DeadLetter(source.key(),
                failure.map(DeserializationException::getData).orElse(new byte[0]),
                DeadLetterReason.DESERIALIZATION,
                failure.map(Throwable::getMessage).orElse("record has no value"),
                source.topic(), source.partition(), source.offset());
    }
}

package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Processes one Kafka batch: process the readable records, dead-letter what could not be read or stored, then
 * commit offsets. Every step is idempotent, so any failure before the commit simply re-runs the whole batch:
 * the Redis window ignores known eventIds and ClickHouse collapses re-inserted rows.
 * Transient failures are rethrown; the container's error handler retries the batch with backoff.
 */
@Slf4j
@Component
@ConditionalOnRole(Role.PROCESSING)
public class DlrBatchConsumer {

    private static final LogAccessor LOG_ACCESSOR = new LogAccessor(DlrBatchConsumer.class);

    private final BatchProcessingService processingService;
    private final DeadLetterSink deadLetterSink;
    private final ObjectMapper objectMapper;
    private final ProcessingMetrics metrics;

    public DlrBatchConsumer(BatchProcessingService processingService, DeadLetterSink deadLetterSink,
                            ObjectMapper objectMapper, ProcessingMetrics metrics) {
        this.processingService = processingService;
        this.deadLetterSink = deadLetterSink;
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

        List<DeadLetter> unstored = processingService.process(List.copyOf(sources.keySet())).stream()
                .map(u -> deadLetter(sourceOf(u.event(), sources), u.reason(), u.error()))
                .toList();

        List<DeadLetter> deadLetters = Stream.concat(unreadable.stream(), unstored.stream()).toList();
        deadLetterSink.publish(deadLetters);
        acknowledgment.acknowledge();
        metrics.recordDeadLetters(deadLetters);
        log.debug("Processed batch of {} records ({} dead-lettered)", records.size(), unreadable.size() + unstored.size());
    }

    // processing reports the instances it was given; a copy would not be found and fails the batch loudly
    private static ConsumerRecord<String, IngestedEvent> sourceOf(
            IngestedEvent event, Map<IngestedEvent, ConsumerRecord<String, IngestedEvent>> sources) {
        return Optional.ofNullable(sources.get(event)).orElseThrow(() -> new IllegalStateException(
                "Processing reported event " + event.event().eventId() + " that is not in the batch"));
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

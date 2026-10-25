package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.KafkaTopicsProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Publishes records processing could not store to the dead-letter topic and waits for the broker to
 * acknowledge them, so the source offsets can safely be committed afterwards.
 */
@Slf4j
public class KafkaDeadLetterSink implements DeadLetterSink {

    public static final String REASON_HEADER = "miniwsa-dlt-reason";

    private final KafkaTemplate<String, byte[]> deadLetterTemplate;
    private final KafkaTopicsProperties topics;

    public KafkaDeadLetterSink(KafkaTemplate<String, byte[]> deadLetterTemplate, KafkaTopicsProperties topics) {
        this.deadLetterTemplate = deadLetterTemplate;
        this.topics = topics;
    }

    @Override
    public void publish(List<DeadLetter> deadLetters) {
        if (deadLetters.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] sends = deadLetters.stream()
                .map(letter -> deadLetterTemplate.send(toRecord(letter)))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sends).get();
        } catch (ExecutionException e) {
            throw new DeadLetterPublishException("Dead-letter publish failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeadLetterPublishException("Interrupted while publishing dead letters", e);
        }
        log.warn("Published {} records to {}", deadLetters.size(), topics.dltTopic());
    }

    private ProducerRecord<String, byte[]> toRecord(DeadLetter letter) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topics.dltTopic(), letter.key(), letter.value());
        Headers headers = record.headers();
        headers.add(REASON_HEADER, utf8(letter.reason().name()));
        headers.add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, utf8(String.valueOf(letter.error())));
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, utf8(letter.sourceTopic()));
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(Integer.BYTES).putInt(letter.sourcePartition()).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.BYTES).putLong(letter.sourceOffset()).array());
        return record;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

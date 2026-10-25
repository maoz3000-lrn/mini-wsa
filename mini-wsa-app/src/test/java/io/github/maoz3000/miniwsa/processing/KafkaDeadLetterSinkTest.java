package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.fixtures.PropertiesFixtures;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KafkaDeadLetterSinkTest {

    private static final DeadLetter LETTER = new DeadLetter("evt-1", "{\"raw\":true}".getBytes(StandardCharsets.UTF_8),
            DeadLetterReason.ROW_VALIDATION, "statusCode out of UInt16 range", "dlr-raw", 3, 42L);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);

    private final KafkaDeadLetterSink publisher = new KafkaDeadLetterSink(template, PropertiesFixtures.kafkaTopics());

    @Test
    @SuppressWarnings("unchecked")
    void publishesOriginalBytesWithReasonAndOriginHeaders() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ArgumentCaptor<ProducerRecord<String, byte[]>> sent = ArgumentCaptor.forClass(ProducerRecord.class);

        publisher.publish(List.of(LETTER));

        verify(template).send(sent.capture());
        ProducerRecord<String, byte[]> record = sent.getValue();
        Map<String, byte[]> headers = Arrays.stream(record.headers().toArray())
                .collect(Collectors.toMap(Header::key, Header::value));
        assertThat(record.topic()).isEqualTo("dlr-raw.DLT");
        assertThat(record.key()).isEqualTo("evt-1");
        assertThat(record.value()).isEqualTo(LETTER.value());
        assertThat(new String(headers.get(KafkaDeadLetterSink.REASON_HEADER), StandardCharsets.UTF_8)).isEqualTo("ROW_VALIDATION");
        assertThat(new String(headers.get(KafkaHeaders.DLT_EXCEPTION_MESSAGE), StandardCharsets.UTF_8))
                .isEqualTo("statusCode out of UInt16 range");
        assertThat(new String(headers.get(KafkaHeaders.DLT_ORIGINAL_TOPIC), StandardCharsets.UTF_8)).isEqualTo("dlr-raw");
        assertThat(ByteBuffer.wrap(headers.get(KafkaHeaders.DLT_ORIGINAL_PARTITION)).getInt()).isEqualTo(3);
        assertThat(ByteBuffer.wrap(headers.get(KafkaHeaders.DLT_ORIGINAL_OFFSET)).getLong()).isEqualTo(42L);
    }

    @Test
    void skipsEmptyBatch() {
        publisher.publish(List.of());

        verifyNoInteractions(template);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsWhenBrokerRejects() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher.publish(List.of(LETTER)))
                .isInstanceOf(DeadLetterPublishException.class)
                .hasRootCauseMessage("broker down");
    }
}

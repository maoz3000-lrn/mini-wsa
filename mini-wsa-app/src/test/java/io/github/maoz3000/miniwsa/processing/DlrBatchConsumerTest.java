package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.domain.error.PermanentStorageException;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.storage.EnrichedEventWriter;
import io.github.maoz3000.miniwsa.storage.RejectedEvent;
import io.github.maoz3000.miniwsa.storage.SaveResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlrBatchConsumerTest {

    private final EnrichmentService enrichment = mock(EnrichmentService.class);
    private final EnrichedEventWriter writer = mock(EnrichedEventWriter.class);
    private final DeadLetterPublisher deadLetters = mock(DeadLetterPublisher.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DlrBatchConsumer consumer = new DlrBatchConsumer(enrichment, writer, deadLetters,
            JsonMapper.builder().build(), new ProcessingMetrics(registry, Clock.fixed(EVENT_TIME.plusSeconds(4), ZoneOffset.UTC)));

    private final IngestedEvent first = new IngestedEvent(validEvent().toBuilder().eventId("a").build(), EVENT_TIME);
    private final IngestedEvent second = new IngestedEvent(validEvent().toBuilder().eventId("b").build(), EVENT_TIME);
    private final EnrichedEvent firstEnriched = new EnrichedEvent(first, "SQL/Command Injection", 75);
    private final EnrichedEvent secondEnriched = new EnrichedEvent(second, "SQL/Command Injection", 75);
    private final List<ConsumerRecord<String, IngestedEvent>> records = List.of(
            new ConsumerRecord<>("dlr-raw", 1, 10L, "a", first),
            new ConsumerRecord<>("dlr-raw", 2, 20L, "b", second));

    @BeforeEach
    void setUp() {
        when(enrichment.enrich(anyList())).thenReturn(List.of(firstEnriched, secondEnriched));
    }

    @Test
    void storesBatchAndAcknowledges() {
        when(writer.saveAll(List.of(firstEnriched, secondEnriched))).thenReturn(new SaveResult(List.of()));

        consumer.onBatch(records, ack);

        verify(deadLetters).publish(List.of());
        verify(ack).acknowledge();
        assertThat(registry.get("miniwsa.events.stored").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("miniwsa.processing.lag").timer().totalTime(TimeUnit.SECONDS))
                .isEqualTo(8.0);
    }

    @Test
    void deadLettersOnlyRejectedEvents() {
        when(writer.saveAll(anyList())).thenReturn(new SaveResult(List.of(new RejectedEvent(secondEnriched, "bad row"))));

        consumer.onBatch(records, ack);

        assertThat(publishedLetters()).singleElement().satisfies(letter -> assertThat(letter)
                .extracting(DeadLetter::key, DeadLetter::reason, DeadLetter::error, DeadLetter::sourcePartition,
                        DeadLetter::sourceOffset)
                .containsExactly("b", DeadLetterReason.ROW_VALIDATION, "bad row", 2, 20L));
        verify(ack).acknowledge();
        assertThat(registry.get("miniwsa.events.stored").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("miniwsa.dead.letters").tag("reason", "ROW_VALIDATION").counter().count()).isEqualTo(1.0);
    }

    @Test
    void deadLettersWholeBatchWhenStoreRejectsIt() {
        when(writer.saveAll(anyList())).thenThrow(new PermanentStorageException("rejected", new RuntimeException()));

        consumer.onBatch(records, ack);

        assertThat(publishedLetters()).extracting(DeadLetter::key, DeadLetter::reason).containsExactlyInAnyOrder(
                tuple("a", DeadLetterReason.STORAGE_REJECTED),
                tuple("b", DeadLetterReason.STORAGE_REJECTED));
        verify(ack).acknowledge();
    }

    @Test
    void rethrowsTransientFailureWithoutAcknowledging() {
        when(writer.saveAll(anyList())).thenThrow(new TransientStorageException("down", new RuntimeException()));

        assertThatThrownBy(() -> consumer.onBatch(records, ack)).isInstanceOf(TransientStorageException.class);

        verify(deadLetters, never()).publish(anyList());
        verify(ack, never()).acknowledge();
    }

    @Test
    void deadLettersUnreadableRecords() {
        when(enrichment.enrich(anyList())).thenReturn(List.of());
        List<ConsumerRecord<String, IngestedEvent>> unreadable = List.of(new ConsumerRecord<>("dlr-raw", 0, 5L, "x", null));

        consumer.onBatch(unreadable, ack);

        assertThat(publishedLetters()).singleElement()
                .extracting(DeadLetter::key, DeadLetter::reason, DeadLetter::sourceOffset)
                .containsExactly("x", DeadLetterReason.DESERIALIZATION, 5L);
        verify(writer, never()).saveAll(anyList());
        verify(ack).acknowledge();
    }

    @SuppressWarnings("unchecked")
    private List<DeadLetter> publishedLetters() {
        ArgumentCaptor<List<DeadLetter>> captor = ArgumentCaptor.forClass(List.class);
        verify(deadLetters).publish(captor.capture());
        return captor.getValue();
    }
}

package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.domain.error.PermanentStorageException;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.storage.EnrichedEventWriter;
import io.github.maoz3000.miniwsa.storage.RejectedEvent;
import io.github.maoz3000.miniwsa.storage.SaveResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchProcessingServiceTest {

    private final EnrichmentService enrichment = mock(EnrichmentService.class);
    private final EnrichedEventWriter writer = mock(EnrichedEventWriter.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BatchProcessingService service = new BatchProcessingService(enrichment, writer,
            new ProcessingMetrics(registry, Clock.fixed(EVENT_TIME.plusSeconds(4), ZoneOffset.UTC)));

    private final IngestedEvent first = new IngestedEvent(validEvent().toBuilder().eventId("a").build(), EVENT_TIME);
    private final IngestedEvent second = new IngestedEvent(validEvent().toBuilder().eventId("b").build(), EVENT_TIME);
    private final EnrichedEvent firstEnriched = new EnrichedEvent(first, "SQL/Command Injection", 75);
    private final EnrichedEvent secondEnriched = new EnrichedEvent(second, "SQL/Command Injection", 75);
    private final List<IngestedEvent> batch = List.of(first, second);

    @BeforeEach
    void setUp() {
        when(enrichment.enrich(batch)).thenReturn(List.of(firstEnriched, secondEnriched));
    }

    @Test
    void storesBatchAndReportsNothing() {
        when(writer.saveAll(List.of(firstEnriched, secondEnriched))).thenReturn(new SaveResult(List.of()));

        assertThat(service.process(batch)).isEmpty();

        assertThat(registry.get("miniwsa.events.stored").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("miniwsa.processing.lag").timer().totalTime(TimeUnit.SECONDS)).isEqualTo(8.0);
    }

    @Test
    void reportsRejectedRowsAsTheInstancesItWasGiven() {
        when(writer.saveAll(anyList())).thenReturn(new SaveResult(List.of(new RejectedEvent(secondEnriched, "bad row"))));

        List<UnstoredEvent> unstored = service.process(batch);

        assertThat(unstored).containsExactly(new UnstoredEvent(second, DeadLetterReason.ROW_VALIDATION, "bad row"));
        assertThat(unstored.getFirst().event()).isSameAs(second);
        assertThat(registry.get("miniwsa.events.stored").counter().count()).isEqualTo(1.0);
    }

    @Test
    void reportsWholeBatchWhenStoreRejectsIt() {
        when(writer.saveAll(anyList())).thenThrow(new PermanentStorageException("rejected", new RuntimeException()));

        List<UnstoredEvent> unstored = service.process(batch);

        assertThat(unstored).containsExactly(
                new UnstoredEvent(first, DeadLetterReason.STORAGE_REJECTED, "rejected"),
                new UnstoredEvent(second, DeadLetterReason.STORAGE_REJECTED, "rejected"));
        assertThat(unstored).extracting(UnstoredEvent::event).allSatisfy(e -> assertThat(batch).anyMatch(b -> b == e));
    }

    @Test
    void rethrowsTransientStoreFailure() {
        when(writer.saveAll(anyList())).thenThrow(new TransientStorageException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.process(batch)).isInstanceOf(TransientStorageException.class);
    }

    @Test
    void rethrowsTransientWindowFailureWithoutStoring() {
        when(enrichment.enrich(batch)).thenThrow(new TransientStorageException("redis down", new RuntimeException()));

        assertThatThrownBy(() -> service.process(batch)).isInstanceOf(TransientStorageException.class);

        verify(writer, never()).saveAll(anyList());
    }

    @Test
    void emptyBatchStoresNothing() {
        assertThat(service.process(List.of())).isEmpty();

        verify(writer, never()).saveAll(anyList());
    }
}

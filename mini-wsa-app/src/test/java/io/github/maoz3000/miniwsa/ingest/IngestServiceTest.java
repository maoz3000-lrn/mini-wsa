package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IngestServiceTest {

    private static final Instant NOW = EVENT_TIME.plusSeconds(3);

    private final EventPublisher publisher = mock(EventPublisher.class);
    private final IngestService service = new IngestService(publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void stampsTheBatchWithOneReceivedAtAndPublishesIt() {
        DlrEvent first = validEvent();
        DlrEvent second = validEvent().toBuilder().eventId("evt-2").build();

        IngestResult result = service.ingest(List.of(first, second));

        assertThat(result).isEqualTo(new IngestResult(2, NOW));
        verify(publisher).publish(List.of(new IngestedEvent(first, NOW), new IngestedEvent(second, NOW)));
    }

    @Test
    void propagatesPublishFailure() {
        doThrow(new EventPublishException("no ack", new RuntimeException())).when(publisher).publish(anyList());

        assertThatThrownBy(() -> service.ingest(List.of(validEvent()))).isInstanceOf(EventPublishException.class);
    }
}

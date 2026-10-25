package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.fixtures.PropertiesFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventPublisherTest {

    private static final IngestedEvent FIRST = new IngestedEvent(validEvent(), EVENT_TIME);
    private static final IngestedEvent SECOND =
            new IngestedEvent(validEvent().toBuilder().eventId("evt-2").build(), EVENT_TIME);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, IngestedEvent> kafkaTemplate = mock(KafkaTemplate.class);

    private final KafkaEventPublisher publisher = new KafkaEventPublisher(kafkaTemplate,
            PropertiesFixtures.kafkaTopics(),
            new IngestProperties(DataSize.ofMegabytes(5), 1000, Duration.ofMinutes(5), Duration.ofMillis(200)));

    @Test
    void sendsEachEventKeyedByEventIdAndWaitsForAcks() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(acked());

        assertThatCode(() -> publisher.publish(List.of(FIRST, SECOND))).doesNotThrowAnyException();

        verify(kafkaTemplate).send("dlr-raw", "evt-00132", FIRST);
        verify(kafkaTemplate).send("dlr-raw", "evt-2", SECOND);
    }

    @Test
    void failsWhenAnySendIsRejected() {
        when(kafkaTemplate.send("dlr-raw", "evt-00132", FIRST)).thenReturn(acked());
        when(kafkaTemplate.send("dlr-raw", "evt-2", SECOND))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher.publish(List.of(FIRST, SECOND)))
                .isInstanceOf(EventPublishException.class)
                .hasRootCauseMessage("broker down");
    }

    @Test
    void failsWhenAcksDoNotArriveInTime() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> publisher.publish(List.of(FIRST)))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("within PT0.2S");
    }

    private static CompletableFuture<SendResult<String, IngestedEvent>> acked() {
        return CompletableFuture.completedFuture(null);
    }
}

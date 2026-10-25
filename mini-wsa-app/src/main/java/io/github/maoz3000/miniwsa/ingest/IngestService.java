package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Stamps a validated batch with {@code receivedAt} and hands it to the processing pipeline.
 */
@Slf4j
@Service
@ConditionalOnRole(Role.INGEST)
public class IngestService {

    private final EventPublisher publisher;
    private final Clock clock;

    public IngestService(EventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    public IngestResult ingest(List<DlrEvent> events) {
        Instant receivedAt = clock.instant();
        List<IngestedEvent> ingested = events.stream()
                .map(event -> new IngestedEvent(event, receivedAt))
                .toList();
        publisher.publish(ingested);
        log.debug("Accepted {} events received at {}", ingested.size(), receivedAt);
        return new IngestResult(ingested.size(), receivedAt);
    }
}

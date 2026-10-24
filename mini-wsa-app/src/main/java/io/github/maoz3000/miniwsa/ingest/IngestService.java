package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.IngestResponse;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Validates an ingest request as a whole, stamps {@code receivedAt} and hands the batch to Kafka.
 */
@Slf4j
@Service
@ConditionalOnRole(Role.INGEST)
public class IngestService {

    private final IngestPayloadParser parser;
    private final IngestEventValidator validator;
    private final DlrEventPublisher publisher;
    private final Clock clock;

    public IngestService(IngestPayloadParser parser, IngestEventValidator validator, DlrEventPublisher publisher,
                         Clock clock) {
        this.parser = parser;
        this.validator = validator;
        this.publisher = publisher;
        this.clock = clock;
    }

    public IngestResponse ingest(JsonNode body) {
        Instant receivedAt = clock.instant();
        List<IngestedEvent> events = validator.validate(parser.toBatch(body)).stream()
                .map(event -> new IngestedEvent(event, receivedAt))
                .toList();
        publisher.publish(events);
        log.debug("Accepted {} events received at {}", events.size(), receivedAt);
        return new IngestResponse(events.size(), receivedAt);
    }
}

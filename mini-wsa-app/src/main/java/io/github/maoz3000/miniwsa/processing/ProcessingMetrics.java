package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Pipeline health as seen by processing:
 * <ul>
 *   <li>{@code miniwsa.delivery.lag}: receivedAt - event timestamp (how late the edge delivers)</li>
 *   <li>{@code miniwsa.processing.lag}: store time - receivedAt (how stale queries are)</li>
 *   <li>{@code miniwsa.events.stored} and {@code miniwsa.dead.letters} (tag {@code reason})</li>
 * </ul>
 */
@Component
@ConditionalOnRole(Role.PROCESSING)
public class ProcessingMetrics {

    private final MeterRegistry registry;
    private final Clock clock;
    private final Timer deliveryLag;
    private final Timer processingLag;
    private final Counter stored;

    public ProcessingMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.deliveryLag = Timer.builder("miniwsa.delivery.lag")
                .description("Event receivedAt minus event timestamp")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.processingLag = Timer.builder("miniwsa.processing.lag")
                .description("Time from ingestion to storage")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.stored = Counter.builder("miniwsa.events.stored").register(registry);
    }

    public void recordStored(List<EnrichedEvent> events) {
        events.forEach(e -> {
            deliveryLag.record(nonNegative(Duration.between(e.event().timestamp(), e.receivedAt())));
            processingLag.record(nonNegative(Duration.between(e.receivedAt(), clock.instant())));
        });
        stored.increment(events.size());
    }

    public void recordDeadLetters(List<DeadLetter> deadLetters) {
        deadLetters.forEach(d -> registry.counter("miniwsa.dead.letters", "reason", d.reason().name()).increment());
    }

    private static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}

package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Classifies and scores a batch. Events are processed in event-time order so the repeat-offender window
 * sees each IP's events chronologically.
 */
@Service
@ConditionalOnRole(Role.PROCESSING)
public class EnrichmentService {

    private static final Comparator<IngestedEvent> EVENT_TIME_ORDER = Comparator
            .comparing((IngestedEvent e) -> e.event().timestamp())
            .thenComparing(e -> e.event().eventId());

    private final AttackTypeClassifier classifier;
    private final ThreatScoreCalculator calculator;
    private final RepeatOffenderCounter window;
    private final RepeatOffenderProperties properties;

    public EnrichmentService(AttackTypeClassifier classifier, ThreatScoreCalculator calculator,
                             RepeatOffenderCounter window, RepeatOffenderProperties properties) {
        this.classifier = classifier;
        this.calculator = calculator;
        this.window = window;
        this.properties = properties;
    }

    /**
     * @return the enriched events, sorted by event time
     */
    public List<EnrichedEvent> enrich(List<IngestedEvent> batch) {
        List<IngestedEvent> ordered = batch.stream().sorted(EVENT_TIME_ORDER).toList();
        List<Long> priorCounts = window.recordAndCountPrior(ordered.stream().map(IngestedEvent::event).toList());
        return IntStream.range(0, ordered.size())
                .mapToObj(i -> enrich(ordered.get(i), priorCounts.get(i) >= properties.minPriorEvents()))
                .toList();
    }

    private EnrichedEvent enrich(IngestedEvent ingested, boolean repeatOffender) {
        return new EnrichedEvent(ingested,
                classifier.classify(ingested.event().rule().category()),
                calculator.score(ingested.event(), repeatOffender));
    }
}

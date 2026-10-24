package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrichmentServiceTest {

    private static final Duration RECEIVED_AFTER = Duration.ofSeconds(3);

    private final RepeatOffenderWindow window = mock(RepeatOffenderWindow.class);

    private final EnrichmentService service = new EnrichmentService(new AttackTypeClassifier(),
            new ThreatScoreCalculator(), window,
            new RepeatOffenderProperties(Duration.ofMinutes(10), 6, 6, Duration.ofMinutes(5), "ro:"));

    @Test
    void enrichesInEventTimeOrderAndAppliesRepeatBonusFromSixPriorEvents() {
        IngestedEvent late = ingested("late", 2);
        IngestedEvent early = ingested("early", 0);
        IngestedEvent middle = ingested("middle", 1);
        when(window.recordAndCountPrior(anyList())).thenReturn(List.of(0L, 5L, 6L));

        List<EnrichedEvent> enriched = service.enrich(List.of(late, early, middle));

        verify(window).recordAndCountPrior(List.of(early.event(), middle.event(), late.event()));
        // example event: CRITICAL (40) + DENY (20) + /login (15) = 75, +15 as repeat offender
        assertThat(enriched).containsExactly(
                new EnrichedEvent(early, "SQL/Command Injection", 75),
                new EnrichedEvent(middle, "SQL/Command Injection", 75),
                new EnrichedEvent(late, "SQL/Command Injection", 90));
    }

    private static IngestedEvent ingested(String eventId, int secondsAfter) {
        DlrEvent event = validEvent().toBuilder()
                .eventId(eventId)
                .timestamp(EVENT_TIME.plusSeconds(secondsAfter))
                .build();
        return new IngestedEvent(event, EVENT_TIME.plus(RECEIVED_AFTER));
    }
}

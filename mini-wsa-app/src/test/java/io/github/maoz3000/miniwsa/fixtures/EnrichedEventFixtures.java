package io.github.maoz3000.miniwsa.fixtures;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;

import java.time.Duration;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;

public final class EnrichedEventFixtures {

    public static final Duration DELIVERY_DELAY = Duration.ofSeconds(2);

    private EnrichedEventFixtures() {
    }

    public static EnrichedEvent enriched(DlrEvent event, int threatScore) {
        return new EnrichedEvent(new IngestedEvent(event, event.timestamp().plus(DELIVERY_DELAY)),
                event.rule().category().attackType(), threatScore);
    }

    public static EnrichedEvent enrichedExample() {
        return enriched(validEvent(), 75);
    }
}

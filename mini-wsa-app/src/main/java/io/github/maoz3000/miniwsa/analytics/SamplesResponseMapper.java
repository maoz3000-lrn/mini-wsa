package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.SampleEventResponse;
import io.github.maoz3000.miniwsa.api.SamplesPage;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.query.PageRequest;

/**
 * Renders a page of samples for clients.
 */
final class SamplesResponseMapper {

    private SamplesResponseMapper() {
    }

    static SamplesPage toResponse(SamplesResult result, PageRequest page) {
        return new SamplesPage(result.total(), page.limit(), page.offset(),
                result.events().stream().map(SamplesResponseMapper::toResponse).toList());
    }

    private static SampleEventResponse toResponse(EnrichedEvent e) {
        return new SampleEventResponse(e.event(), e.receivedAt(), e.attackType(), e.threatScore());
    }
}

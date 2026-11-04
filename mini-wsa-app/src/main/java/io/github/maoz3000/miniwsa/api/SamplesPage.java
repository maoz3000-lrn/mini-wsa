package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.analytics.SamplesResult;
import io.github.maoz3000.miniwsa.domain.analytics.samples.PageRequest;

import java.util.List;

/**
 * @param total  number of events matching the filters (independent of limit/offset)
 * @param events the requested page, newest first
 */
public record SamplesPage(long total, int limit, int offset, List<SampleEventResponse> events) {

    public static SamplesPage from(SamplesResult result, PageRequest page) {
        return new SamplesPage(result.total(), page.limit(), page.offset(),
                result.events().stream().map(SampleEventResponse::from).toList());
    }
}

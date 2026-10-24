package io.github.maoz3000.miniwsa.api;

import java.util.List;

/**
 * @param total  number of events matching the filters (independent of limit/offset)
 * @param events the requested page, newest first
 */
public record SamplesPage(long total, int limit, int offset, List<SampleEventResponse> events) {
}

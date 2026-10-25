package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PageRequest;
import io.github.maoz3000.miniwsa.storage.EventAnalyticsReader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnRole(Role.ANALYTICS)
public class SamplesService {

    /** Page size limits (assignment spec). */
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final EventAnalyticsReader reader;

    public SamplesService(EventAnalyticsReader reader) {
        this.reader = reader;
    }

    public SamplesResult samples(EventFilter filter, PageRequest page) {
        return VirtualThreads.fanOut(executor -> {
            var events = CompletableFuture.supplyAsync(() -> reader.findSamples(filter, page), executor);
            var total = CompletableFuture.supplyAsync(() -> reader.countEvents(filter), executor);
            List<EnrichedEvent> found = events.join();
            return new SamplesResult(total.join(), found,
                    PageCursor.next(filter.to(), page.offset(), found, page.limit()));
        });
    }
}

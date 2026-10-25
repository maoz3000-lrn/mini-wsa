package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.domain.error.PermanentStorageException;
import io.github.maoz3000.miniwsa.storage.EnrichedEventWriter;
import io.github.maoz3000.miniwsa.storage.SaveResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Enriches and stores one batch, and reports the events that could not be stored because of their data.
 * Transient failures (store or Redis unavailable) are not handled here: they propagate so the caller does not
 * commit, and the whole batch is processed again. That is safe because every step is idempotent per eventId.
 */
@Slf4j
@Service
@ConditionalOnRole(Role.PROCESSING)
public class BatchProcessingService {

    private final EnrichmentService enrichmentService;
    private final EnrichedEventWriter writer;
    private final ProcessingMetrics metrics;

    public BatchProcessingService(EnrichmentService enrichmentService, EnrichedEventWriter writer,
                                  ProcessingMetrics metrics) {
        this.enrichmentService = enrichmentService;
        this.writer = writer;
        this.metrics = metrics;
    }

    /**
     * @return the events that were not stored; every other event of the batch was stored
     */
    public List<UnstoredEvent> process(List<IngestedEvent> events) {
        List<EnrichedEvent> enriched = enrichmentService.enrich(events);
        if (enriched.isEmpty()) {
            return List.of();
        }
        try {
            SaveResult result = writer.saveAll(enriched);
            metrics.recordStored(stored(enriched, result));
            return result.rejected().stream()
                    .map(r -> new UnstoredEvent(r.event().ingested(), DeadLetterReason.ROW_VALIDATION, r.reason()))
                    .toList();
        } catch (PermanentStorageException e) {
            log.error("Store rejected a batch of {} events; reporting all of them as unstored", enriched.size(), e);
            return enriched.stream()
                    .map(ev -> new UnstoredEvent(ev.ingested(), DeadLetterReason.STORAGE_REJECTED, e.getMessage()))
                    .toList();
        }
    }

    private static List<EnrichedEvent> stored(List<EnrichedEvent> enriched, SaveResult result) {
        Set<EnrichedEvent> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
        result.rejected().forEach(r -> rejected.add(r.event()));
        return enriched.stream().filter(e -> !rejected.contains(e)).toList();
    }
}

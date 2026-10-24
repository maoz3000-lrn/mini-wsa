package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@ConditionalOnRole(Role.ANALYTICS)
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Aggregated statistics; every parameter is optional, {@code from}/{@code to} are inclusive ISO-8601 instants.
     */
    @GetMapping(ApiPaths.STATS_SUMMARY)
    public StatsSummaryResponse summary(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return statsService.summary(EventFilter.builder().configId(configId).from(from).to(to).build());
    }
}

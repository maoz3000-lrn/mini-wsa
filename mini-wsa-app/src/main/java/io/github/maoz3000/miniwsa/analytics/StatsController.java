package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
    @Operation(summary = "Aggregated statistics",
            description = "Totals by category and action (zero-filled, always adding up to totalEvents), "
                    + "top 10 attackers and top 10 targeted paths.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters (e.g. from after to)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "ClickHouse unavailable; retry after Retry-After seconds",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping(ApiPaths.STATS_SUMMARY)
    public StatsSummaryResponse summary(
            @Parameter(description = "Configuration id; omit to aggregate across all configurations", example = "14227")
            @RequestParam(required = false) Long configId,
            @Parameter(description = "Inclusive lower bound on the event timestamp (ISO-8601)", example = "2026-05-20T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive upper bound on the event timestamp (ISO-8601)", example = "2026-05-21T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return StatsResponseMapper.toResponse(
                statsService.summary(EventFilter.builder().configId(configId).from(from).to(to).build()));
    }
}

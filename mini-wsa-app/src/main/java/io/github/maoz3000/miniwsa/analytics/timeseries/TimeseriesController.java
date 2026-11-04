package io.github.maoz3000.miniwsa.analytics.timeseries;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.TimeseriesResponse;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.analytics.EventFilter;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.TimeInterval;
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
public class TimeseriesController {

    private final TimeseriesService timeseriesService;

    public TimeseriesController(TimeseriesService timeseriesService) {
        this.timeseriesService = timeseriesService;
    }

    /**
     * Event counts per bucket for a line chart. {@code from}/{@code to} are inclusive ISO-8601 instants;
     * {@code to} defaults to now and {@code from} to {@code to} minus 1h, 24h or 7d for 1m, 5m or 1h.
     */
    @Operation(summary = "Event counts over time",
            description = "Counts per UTC bucket aligned to the interval, oldest first, with empty buckets as 0. "
                    + "At most " + TimeseriesService.MAX_BUCKETS + " buckets. timeRange is the range actually "
                    + "covered, with defaults applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time series"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid parameters (unknown interval, from after to, too many buckets)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "ClickHouse unavailable; retry after Retry-After seconds",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping(ApiPaths.STATS_TIMESERIES)
    public TimeseriesResponse timeseries(
            @Parameter(description = "Configuration id; omit to aggregate across all configurations", example = "14227")
            @RequestParam(required = false) Long configId,
            @Parameter(description = "Inclusive lower bound on the event timestamp (ISO-8601); "
                    + "defaults to to minus 1h (1m), 24h (5m) or 7d (1h)", example = "2026-05-20T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive upper bound on the event timestamp (ISO-8601); defaults to now",
                    example = "2026-05-21T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Bucket width",
                    schema = @Schema(type = "string", allowableValues = {"1m", "5m", "1h"}))
            @RequestParam String interval) {
        // bound as text: an enum parameter would also accept constant names such as ONE_MINUTE
        return TimeseriesResponse.from(timeseriesService.timeseries(
                EventFilter.builder().configId(configId).from(from).to(to).build(), TimeInterval.parse(interval)));
    }
}

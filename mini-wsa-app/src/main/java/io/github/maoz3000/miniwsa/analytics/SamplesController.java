package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.OpenApiExamples;
import io.github.maoz3000.miniwsa.api.SamplesPage;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;

@RestController
@ConditionalOnRole(Role.ANALYTICS)
public class SamplesController {

    private final SamplesService samplesService;

    public SamplesController(SamplesService samplesService) {
        this.samplesService = samplesService;
    }

    /**
     * Enriched events newest first. All filters are optional; {@code from}/{@code to} are inclusive.
     * Unless this is the last page, a {@code Link: <...>; rel="next"} header points to the next page using
     * {@code to}+{@code offset} as a cursor, so newly arriving events cannot shift pages.
     */
    @Operation(summary = "Enriched event samples, newest first",
            description = "Individual events with their enrichment and the total number of matching events. "
                    + "Page with the Link header (to + offset act as a cursor).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of events",
                    headers = @Header(name = HttpHeaders.LINK, description = OpenApiExamples.LINK_HEADER_DESCRIPTION)),
            @ApiResponse(responseCode = "400", description = "Invalid parameters (e.g. from after to)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "ClickHouse unavailable; retry after Retry-After seconds",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping(ApiPaths.SAMPLES)
    public ResponseEntity<SamplesPage> samples(
            @Parameter(description = "Configuration id; omit to aggregate across all configurations", example = "14227")
            @RequestParam(required = false) Long configId,
            @Parameter(description = "Inclusive lower bound on the event timestamp (ISO-8601)", example = "2026-05-20T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive upper bound on the event timestamp (ISO-8601)", example = "2026-05-21T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Rule category")
            @RequestParam(required = false) RuleCategory category,
            @Parameter(description = "Action taken by the edge")
            @RequestParam(required = false) Action action,
            @Parameter(description = "Page size, 1-100")
            @RequestParam(defaultValue = "" + SamplesService.DEFAULT_LIMIT) @Min(1) @Max(SamplesService.MAX_LIMIT) int limit,
            @Parameter(description = "Events to skip; with a Link cursor only the events tied at the boundary timestamp")
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        EventFilter filter = new EventFilter(configId, from, to, category, action);
        SamplesService.Result result = samplesService.samples(filter, new PageRequest(limit, offset));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        result.next().ifPresent(next -> response.header(HttpHeaders.LINK, nextLink(next)));
        return response.body(result.page());
    }

    private static String nextLink(PageCursor next) {
        String uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("to", next.to().toString())
                .replaceQueryParam("offset", next.offset())
                .build()
                .encode()
                .toUriString();
        return "<" + uri + ">; rel=\"next\"";
    }
}

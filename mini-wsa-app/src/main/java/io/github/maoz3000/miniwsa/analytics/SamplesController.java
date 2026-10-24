package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.SamplesPage;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
    @GetMapping(ApiPaths.SAMPLES)
    public ResponseEntity<SamplesPage> samples(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) RuleCategory category,
            @RequestParam(required = false) Action action,
            @RequestParam(defaultValue = "" + SamplesService.DEFAULT_LIMIT) @Min(1) @Max(SamplesService.MAX_LIMIT) int limit,
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

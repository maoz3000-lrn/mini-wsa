package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.api.DlrEventRequest;
import io.github.maoz3000.miniwsa.api.IngestResponse;
import io.github.maoz3000.miniwsa.api.OpenApiExamples;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnRole(Role.INGEST)
public class IngestController {

    private final IngestPayloadParser parser;
    private final IngestEventValidator validator;
    private final IngestService ingestService;

    public IngestController(IngestPayloadParser parser, IngestEventValidator validator, IngestService ingestService) {
        this.parser = parser;
        this.validator = validator;
        this.ingestService = ingestService;
    }

    /**
     * Accepts a single event object or an array of events. All-or-nothing: any invalid event rejects the batch.
     */
    @Operation(summary = "Ingest one event or a batch",
            description = "Validates every event (all-or-nothing), stamps receivedAt and answers 201 once Kafka "
                    + "acknowledged all of them. Processing and storage happen asynchronously.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(oneOf = {DlrEventRequest.class, DlrEventRequest[].class}),
            examples = {@ExampleObject(name = "single event", value = OpenApiExamples.SINGLE_EVENT),
                    @ExampleObject(name = "batch", value = OpenApiExamples.BATCH)}))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All events durably accepted"),
            @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid event(s); nothing accepted",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "Body over 5 MB or more than 1000 events",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Kafka unavailable; retry after Retry-After seconds",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping(path = ApiPaths.INGEST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResponse ingest(@RequestBody @Parameter(hidden = true) JsonNode body) {
        IngestResult result = ingestService.ingest(validator.validate(parser.toBatch(body)));
        return new IngestResponse(result.accepted(), result.receivedAt());
    }
}

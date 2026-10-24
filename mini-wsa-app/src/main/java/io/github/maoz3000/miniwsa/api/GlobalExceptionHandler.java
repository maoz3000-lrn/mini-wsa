package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.ingest.EventValidationException;
import io.github.maoz3000.miniwsa.ingest.PayloadTooLargeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem details. Each exception is logged once, here.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS_PROPERTY = "errors";
    static final String INTERNAL_ERROR_DETAIL = "Unexpected server error";
    static final String MALFORMED_JSON_DETAIL = "request body is not valid JSON";

    @ExceptionHandler(EventValidationException.class)
    ProblemDetail handleValidation(EventValidationException e) {
        log.warn("Rejected ingest request: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty(ERRORS_PROPERTY, e.errors());
        return problem;
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail handleTooLarge(PayloadTooLargeException e) {
        log.warn("Rejected oversized ingest request: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        if (NestedExceptionUtils.getRootCause(e) instanceof PayloadTooLargeException tooLarge) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(handleTooLarge(tooLarge));
        }
        log.warn("Rejected unreadable request body: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, MALFORMED_JSON_DETAIL));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL);
    }
}

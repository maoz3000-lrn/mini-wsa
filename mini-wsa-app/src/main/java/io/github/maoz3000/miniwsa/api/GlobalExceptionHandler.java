package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.ingest.EventValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem details. Each exception is logged once, here.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS_PROPERTY = "errors";
    static final String INTERNAL_ERROR_DETAIL = "Unexpected server error";

    @ExceptionHandler(EventValidationException.class)
    ProblemDetail handleValidation(EventValidationException e) {
        log.warn("Rejected ingest request: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty(ERRORS_PROPERTY, e.errors());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL);
    }
}

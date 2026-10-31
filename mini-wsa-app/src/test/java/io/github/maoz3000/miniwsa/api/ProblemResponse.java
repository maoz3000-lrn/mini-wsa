package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.ingest.EventError;

import java.util.List;

/**
 * The JSON shape of an RFC 9457 problem response, for comparing whole error bodies in tests.
 *
 * @param errors the per-event problems of an ingest rejection, or {@code null} when the body has none
 */
public record ProblemResponse(String title, int status, String detail, String instance, List<EventError> errors) {
}

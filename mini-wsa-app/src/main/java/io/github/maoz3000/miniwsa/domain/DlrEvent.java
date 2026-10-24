package io.github.maoz3000.miniwsa.domain;

import lombok.Builder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * A security event (Data Log Record) exactly as sent by the edge.
 */
@Builder(toBuilder = true)
public record DlrEvent(
        @NotBlank @Size(max = 128) String eventId,
        @NotNull Instant timestamp,
        @NotNull @Positive Long configId,
        @NotBlank @Size(max = 64) String policyId,
        @NotBlank @IpAddress String clientIp,
        @NotBlank @Size(max = 255) String hostname,
        @NotBlank @Size(max = 2048) String path,
        @NotBlank @Size(max = 16) String method,
        @NotNull @Min(100) @Max(599) Integer statusCode,
        @Size(max = 1024) String userAgent,
        @NotNull @Valid Rule rule,
        @NotNull Action action,
        @Valid GeoLocation geoLocation,
        @PositiveOrZero Long requestSize,
        @PositiveOrZero Long responseSize) {
}

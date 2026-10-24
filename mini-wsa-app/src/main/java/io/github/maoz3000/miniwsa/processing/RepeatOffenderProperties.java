package io.github.maoz3000.miniwsa.processing;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param window          look-back window, measured in event time
 * @param minPriorEvents  earlier events of the same IP needed for the bonus ("more than 5" = 6)
 * @param maxEntries      newest entries kept per IP; must cover minPriorEvents
 * @param allowedLateness grace for late events; key TTL = window + allowedLateness
 * @param keyPrefix       Redis key namespace
 */
@Validated
@ConfigurationProperties("miniwsa.repeat-offender")
public record RepeatOffenderProperties(
        @NotNull Duration window,
        @Positive int minPriorEvents,
        @Positive int maxEntries,
        @NotNull Duration allowedLateness,
        @NotBlank String keyPrefix) {

    @AssertTrue(message = "max-entries must be >= min-prior-events")
    boolean isMaxEntriesSufficient() {
        return maxEntries >= minPriorEvents;
    }

    public Duration keyTtl() {
        return window.plus(allowedLateness);
    }
}

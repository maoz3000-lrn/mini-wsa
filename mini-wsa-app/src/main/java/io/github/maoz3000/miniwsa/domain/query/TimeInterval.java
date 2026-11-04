package io.github.maoz3000.miniwsa.domain.query;

import java.time.Duration;
import java.util.Arrays;

/**
 * Width of a time-series bucket, with the range covered when the client gives no bounds.
 */
public enum TimeInterval {
    ONE_MINUTE("1m", Duration.ofMinutes(1), Duration.ofHours(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5), Duration.ofHours(24)),
    ONE_HOUR("1h", Duration.ofHours(1), Duration.ofDays(7));

    private final String label;
    private final Duration duration;
    private final Duration defaultWindow;

    TimeInterval(String label, Duration duration, Duration defaultWindow) {
        this.label = label;
        this.duration = duration;
        this.defaultWindow = defaultWindow;
    }

    /** Wire format, e.g. {@code 5m}. */
    public String label() {
        return label;
    }

    public Duration duration() {
        return duration;
    }

    /** Range ending at {@code to} that is used when {@code from} is not given. */
    public Duration defaultWindow() {
        return defaultWindow;
    }

    /**
     * @throws InvalidQueryException unless {@code label} is exactly one of the labels, e.g. {@code 5m}
     */
    public static TimeInterval parse(String label) {
        return Arrays.stream(values()).filter(i -> i.label.equals(label)).findFirst()
                .orElseThrow(() -> new InvalidQueryException("interval must be one of "
                        + Arrays.stream(values()).map(TimeInterval::label).toList() + ", got '" + label + "'"));
    }
}

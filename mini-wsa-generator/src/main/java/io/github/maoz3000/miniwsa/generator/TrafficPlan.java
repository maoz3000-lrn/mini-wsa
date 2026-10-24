package io.github.maoz3000.miniwsa.generator;

import java.time.Duration;
import java.time.Instant;

/**
 * What to generate.
 *
 * @param count       total number of events
 * @param start       earliest event timestamp
 * @param duration    event-time span; events fall in [start, start + duration]
 * @param waveRatio   share of events (0-1) that belong to attack waves
 * @param waveSizeMin smallest wave
 * @param waveSizeMax largest wave
 * @param seed        random seed; the same plan always yields the same events
 */
public record TrafficPlan(int count, Instant start, Duration duration, double waveRatio, int waveSizeMin,
                          int waveSizeMax, long seed) {

    public TrafficPlan {
        if (count < 0 || waveRatio < 0 || waveRatio > 1 || waveSizeMin < 1 || waveSizeMax < waveSizeMin
                || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("invalid traffic plan: " + this);
        }
    }

    Instant end() {
        return start.plus(duration);
    }
}

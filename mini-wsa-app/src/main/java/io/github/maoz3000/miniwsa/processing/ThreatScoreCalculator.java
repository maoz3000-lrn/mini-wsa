package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Threat score (0-100) = severity points + action points + sensitive path bonus + repeat offender bonus, capped.
 * With the current rules the highest reachable score is 90 (CRITICAL + DENY + both bonuses); the cap guards
 * future rule changes.
 */
@Component
@ConditionalOnRole(Role.PROCESSING)
public class ThreatScoreCalculator {

    public static final int SENSITIVE_PATH_BONUS = 15;
    public static final int REPEAT_OFFENDER_BONUS = 15;

    /**
     * Matched as case-insensitive substrings of the raw path (no URL decoding).
     */
    static final List<String> SENSITIVE_PATH_MARKERS = List.of("/admin", "/login");

    public int score(DlrEvent event, boolean repeatOffender) {
        int sum = event.rule().severity().points()
                + event.action().points()
                + (isSensitivePath(event.path()) ? SENSITIVE_PATH_BONUS : 0)
                + (repeatOffender ? REPEAT_OFFENDER_BONUS : 0);
        return clamp(sum);
    }

    static boolean isSensitivePath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return SENSITIVE_PATH_MARKERS.stream().anyMatch(normalized::contains);
    }

    static int clamp(int score) {
        return Math.clamp(score, EnrichedEvent.MIN_THREAT_SCORE, EnrichedEvent.MAX_THREAT_SCORE);
    }
}

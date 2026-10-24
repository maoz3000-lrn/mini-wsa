package io.github.maoz3000.miniwsa.domain;

/**
 * Severity of the WAF rule that fired, with its base contribution to the threat score.
 */
public enum RuleSeverity {
    CRITICAL(40),
    HIGH(30),
    MEDIUM(20),
    LOW(10);

    private final int points;

    RuleSeverity(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }
}

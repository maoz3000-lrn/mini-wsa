package io.github.maoz3000.miniwsa.domain;

/**
 * Category of the WAF rule that fired, with the human-readable attack type it classifies to.
 */
public enum RuleCategory {
    INJECTION("SQL/Command Injection"),
    XSS("Cross-Site Scripting"),
    PROTOCOL_VIOLATION("Protocol Anomaly"),
    DATA_LEAKAGE("Data Exfiltration"),
    BOT("Bot Activity"),
    DOS("Denial of Service"),
    RATE_LIMIT("Rate Limiting");

    private final String attackType;

    RuleCategory(String attackType) {
        this.attackType = attackType;
    }

    public String attackType() {
        return attackType;
    }
}

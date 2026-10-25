package io.github.maoz3000.miniwsa.domain;

import lombok.Builder;

import java.util.Objects;

@Builder(toBuilder = true)
public record Rule(
        String id,
        String name,
        String message,
        RuleSeverity severity,
        RuleCategory category) {

    public Rule {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
    }
}

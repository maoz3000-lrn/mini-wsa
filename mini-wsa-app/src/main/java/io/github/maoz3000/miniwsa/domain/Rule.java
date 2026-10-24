package io.github.maoz3000.miniwsa.domain;

import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Builder(toBuilder = true)
public record Rule(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String message,
        @NotNull RuleSeverity severity,
        @NotNull RuleCategory category) {
}

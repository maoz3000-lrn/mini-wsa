package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.RuleCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AttackTypeClassifierTest {

    private final AttackTypeClassifier classifier = new AttackTypeClassifier();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            INJECTION          | SQL/Command Injection
            XSS                | Cross-Site Scripting
            PROTOCOL_VIOLATION | Protocol Anomaly
            DATA_LEAKAGE       | Data Exfiltration
            BOT                | Bot Activity
            DOS                | Denial of Service
            RATE_LIMIT         | Rate Limiting
            """)
    void mapsCategoryToAttackTypeFromSpec(RuleCategory category, String expectedAttackType) {
        assertThat(classifier.classify(category)).isEqualTo(expectedAttackType);
    }

    @Test
    void everyCategoryHasADistinctAttackType() {
        assertThat(Arrays.stream(RuleCategory.values()).map(classifier::classify)).doesNotHaveDuplicates();
    }
}

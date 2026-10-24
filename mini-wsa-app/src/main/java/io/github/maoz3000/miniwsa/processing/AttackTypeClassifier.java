package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import org.springframework.stereotype.Component;

/**
 * Maps the rule category to the human-readable attack type shown to analysts.
 */
@Component
@ConditionalOnRole(Role.PROCESSING)
public class AttackTypeClassifier {

    public String classify(RuleCategory category) {
        return category.attackType();
    }
}

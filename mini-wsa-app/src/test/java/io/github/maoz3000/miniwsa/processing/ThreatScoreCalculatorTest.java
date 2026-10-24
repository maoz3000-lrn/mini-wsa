package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.RuleSeverity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;

class ThreatScoreCalculatorTest {

    private static final String NEUTRAL_PATH = "/api/v1/orders";

    private final ThreatScoreCalculator calculator = new ThreatScoreCalculator();

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource(textBlock = """
            CRITICAL, DENY,    60
            CRITICAL, ALERT,   50
            CRITICAL, MONITOR, 40
            HIGH,     DENY,    50
            HIGH,     ALERT,   40
            HIGH,     MONITOR, 30
            MEDIUM,   DENY,    40
            MEDIUM,   ALERT,   30
            MEDIUM,   MONITOR, 20
            LOW,      DENY,    30
            LOW,      ALERT,   20
            LOW,      MONITOR, 10
            """)
    void sumsSeverityAndActionPoints(RuleSeverity severity, Action action, int expected) {
        assertThat(calculator.score(event(severity, action, NEUTRAL_PATH), false)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> +{1}")
    @CsvSource(textBlock = """
            /admin/users,          15
            /api/v1/login,         15
            /ADMIN/config.php,     15
            /Login,                15
            /administrator,        15
            /wp-login.php,         0
            /admin/login,          15
            /api/v1/orders,        0
            /adm/in,               0
            /%61dmin,              0
            /%6Cogin,              0
            /api/%2Fadmin%2Fusers, 0
            """)
    void addsSensitivePathBonusOnceForCaseInsensitiveSubstring(String path, int expectedBonus) {
        int score = calculator.score(event(RuleSeverity.LOW, Action.MONITOR, path), false);

        assertThat(score).isEqualTo(RuleSeverity.LOW.points() + expectedBonus);
    }

    @ParameterizedTest(name = "repeatOffender={0} -> {1}")
    @CsvSource(textBlock = """
            false, 60
            true,  75
            """)
    void addsRepeatOffenderBonus(boolean repeatOffender, int expected) {
        assertThat(calculator.score(event(RuleSeverity.CRITICAL, Action.DENY, NEUTRAL_PATH), repeatOffender))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            /admin, true,  90
            /admin, false, 75
            """)
    void highestReachableScoreIs90(String path, boolean repeatOffender, int expected) {
        assertThat(calculator.score(event(RuleSeverity.CRITICAL, Action.DENY, path), repeatOffender))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "clamp({0}) = {1}")
    @CsvSource(textBlock = """
            105, 100
            100, 100
            42,  42
            0,   0
            -5,  0
            """)
    void clampsToZeroToHundred(int raw, int expected) {
        assertThat(ThreatScoreCalculator.clamp(raw)).isEqualTo(expected);
    }

    private static DlrEvent event(RuleSeverity severity, Action action, String path) {
        DlrEvent event = validEvent();
        return event.toBuilder()
                .rule(event.rule().toBuilder().severity(severity).build())
                .action(action)
                .path(path)
                .build();
    }
}

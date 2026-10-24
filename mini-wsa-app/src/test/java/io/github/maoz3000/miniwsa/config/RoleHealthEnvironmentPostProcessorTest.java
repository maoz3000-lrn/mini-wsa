package io.github.maoz3000.miniwsa.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RoleHealthEnvironmentPostProcessorTest {

    @ParameterizedTest(name = "roles={0}")
    @CsvSource(delimiter = '|', textBlock = """
            ingest                       | false | false
            processing                   | true  | true
            analytics                    | false | true
            ingest,analytics             | false | true
            ingest,processing,analytics  | true  | true
            """)
    void disablesHealthChecksOfUnusedInfrastructure(String roles, boolean redisChecked, boolean dbChecked) {
        MockEnvironment environment = new MockEnvironment().withProperty(RolesProperties.PROPERTY, roles);

        new RoleHealthEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.health.redis.enabled", Boolean.class, true)).isEqualTo(redisChecked);
        assertThat(environment.getProperty("management.health.db.enabled", Boolean.class, true)).isEqualTo(dbChecked);
    }

    @ParameterizedTest
    @CsvSource("ingest")
    void explicitSettingsWin(String roles) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(RolesProperties.PROPERTY, roles)
                .withProperty("management.health.redis.enabled", "true");

        new RoleHealthEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.health.redis.enabled", Boolean.class)).isTrue();
    }
}

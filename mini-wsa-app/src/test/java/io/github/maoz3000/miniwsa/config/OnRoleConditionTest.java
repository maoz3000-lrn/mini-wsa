package io.github.maoz3000.miniwsa.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OnRoleConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(RoleBeans.class);

    static Stream<Arguments> roleSelections() {
        return Stream.of(
                Arguments.of("ingest", Set.of("ingestBean")),
                Arguments.of("processing", Set.of("processingBean", "storageBean")),
                Arguments.of("analytics", Set.of("analyticsBean", "storageBean")),
                Arguments.of("INGEST, analytics", Set.of("ingestBean", "analyticsBean", "storageBean")),
                Arguments.of("ingest,processing,analytics",
                        Set.of("ingestBean", "processingBean", "analyticsBean", "storageBean")));
    }

    @ParameterizedTest(name = "roles={0}")
    @MethodSource("roleSelections")
    void registersOnlyBeansOfActiveRoles(String roles, Set<String> expectedBeans) {
        runner.withPropertyValues(RolesProperties.PROPERTY + "=" + roles)
                .run(context -> assertThat(Set.of(context.getBeanNamesForType(String.class)))
                        .isEqualTo(expectedBeans));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ingset", ""})
    void failsStartupOnInvalidRoles(String roles) {
        runner.withPropertyValues(RolesProperties.PROPERTY + "=" + roles)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RolesProperties.class)
    static class RoleBeans {

        @Bean
        @ConditionalOnRole(Role.INGEST)
        String ingestBean() {
            return "ingest";
        }

        @Bean
        @ConditionalOnRole(Role.PROCESSING)
        String processingBean() {
            return "processing";
        }

        @Bean
        @ConditionalOnRole(Role.ANALYTICS)
        String analyticsBean() {
            return "analytics";
        }

        @Bean
        @ConditionalOnRole({Role.PROCESSING, Role.ANALYTICS})
        String storageBean() {
            return "storage";
        }
    }
}

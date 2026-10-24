package io.github.maoz3000.miniwsa.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns off health checks for infrastructure the active roles never use, so e.g. an ingest-only instance
 * does not report DOWN because ClickHouse or Redis is unreachable. Connections to that infrastructure are
 * lazy, so nothing else connects to it either. Added with lowest precedence: explicit settings still win.
 */
public class RoleHealthEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "miniwsaRoleHealthDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Set<Role> roles = Binder.get(environment)
                .bind(RolesProperties.PROPERTY, Bindable.setOf(Role.class))
                .orElse(EnumSet.allOf(Role.class));
        Map<String, Object> defaults = new HashMap<>();
        if (!roles.contains(Role.PROCESSING)) {
            defaults.put("management.health.redis.enabled", false);
        }
        if (!roles.contains(Role.PROCESSING) && !roles.contains(Role.ANALYTICS)) {
            defaults.put("management.health.db.enabled", false);
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}

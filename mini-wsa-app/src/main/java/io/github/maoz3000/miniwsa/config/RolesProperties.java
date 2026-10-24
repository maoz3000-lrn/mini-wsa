package io.github.maoz3000.miniwsa.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.EnumSet;
import java.util.Set;

/**
 * Roles this process runs. Unknown values fail binding; an empty set fails validation.
 */
@Validated
@ConfigurationProperties("miniwsa")
public record RolesProperties(@NotEmpty Set<Role> roles) {

    public static final String PROPERTY = "miniwsa.roles";

    public RolesProperties {
        roles = roles == null ? EnumSet.allOf(Role.class) : Set.copyOf(roles);
    }
}

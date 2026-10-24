package io.github.maoz3000.miniwsa.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

class OnRoleCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Role[] required = (Role[]) metadata.getAnnotationAttributes(ConditionalOnRole.class.getName()).get("value");
        Set<Role> active = Binder.get(context.getEnvironment())
                .bind(RolesProperties.PROPERTY, Bindable.setOf(Role.class))
                .orElse(EnumSet.allOf(Role.class));
        return Arrays.stream(required).anyMatch(active::contains);
    }
}

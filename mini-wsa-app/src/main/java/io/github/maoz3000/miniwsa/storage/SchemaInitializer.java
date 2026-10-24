package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

/**
 * Applies the idempotent ClickHouse schema before readers, writers and the Kafka listener start.
 * While ClickHouse cannot be reached (still starting, DNS not ready) it retries; any other failure, such as
 * invalid SQL, fails startup immediately.
 */
@Slf4j
@Component
@ConditionalOnRole({Role.PROCESSING, Role.ANALYTICS})
public class SchemaInitializer implements InitializingBean {

    static final String SCHEMA_LOCATION = "clickhouse/schema.sql";

    private final DataSource dataSource;
    private final ClickHouseProperties properties;

    public SchemaInitializer(DataSource dataSource, ClickHouseProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (properties.initSchema()) {
            apply(1);
            log.info("ClickHouse schema applied from {}", SCHEMA_LOCATION);
        }
    }

    private void apply(int attempt) {
        try {
            new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_LOCATION)).execute(dataSource);
        } catch (DataAccessException e) {
            if (!isUnreachable(e) || attempt >= properties.initRetries()) {
                throw e;
            }
            log.warn("ClickHouse unreachable for schema initialization (attempt {}/{}): {}; retrying in {}",
                    attempt, properties.initRetries(), rootMessage(e), properties.initRetryInterval());
            pause(e);
            apply(attempt + 1);
        }
    }

    private void pause(DataAccessException pending) {
        try {
            Thread.sleep(properties.initRetryInterval());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            pending.addSuppressed(interrupted);
            throw pending;
        }
    }

    static boolean isUnreachable(Throwable failure) {
        return causes(failure).anyMatch(t -> t instanceof UnknownHostException
                || t instanceof ConnectException
                || t instanceof NoRouteToHostException);
    }

    private static String rootMessage(Throwable failure) {
        return causes(failure).reduce((first, second) -> second).map(Throwable::toString).orElse("");
    }

    private static Stream<Throwable> causes(Throwable failure) {
        return Stream.iterate(failure, t -> t != null, Throwable::getCause);
    }
}

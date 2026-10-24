package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Applies the idempotent ClickHouse schema before readers, writers and the Kafka listener start.
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
            new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_LOCATION)).execute(dataSource);
            log.info("ClickHouse schema applied from {}", SCHEMA_LOCATION);
        }
    }
}

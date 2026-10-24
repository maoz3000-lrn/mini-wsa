package io.github.maoz3000.miniwsa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at /v3/api-docs and browsable at /swagger-ui.html. Each instance documents only the
 * endpoints of its active roles.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String TITLE = "Mini WSA API";
    static final String DESCRIPTION = """
            Ingests security events (DLRs), enriches them (attack type, threat score) and serves analytics.
            Ingestion is asynchronous: 201 means the events are durably queued in Kafka; they become visible to
            the stats and samples endpoints shortly after (usually within 1-2 seconds).""";

    @Bean
    public OpenAPI miniWsaOpenApi() {
        return new OpenAPI().info(new Info().title(TITLE).version("v1").description(DESCRIPTION));
    }
}

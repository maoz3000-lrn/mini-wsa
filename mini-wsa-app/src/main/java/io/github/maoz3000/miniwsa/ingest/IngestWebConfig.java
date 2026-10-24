package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.api.ApiPaths;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(Role.INGEST)
public class IngestWebConfig {

    @Bean
    public FilterRegistrationBean<RequestSizeFilter> requestSizeFilter(IngestProperties properties,
                                                                       ObjectMapper objectMapper) {
        FilterRegistrationBean<RequestSizeFilter> registration = new FilterRegistrationBean<>(
                new RequestSizeFilter(properties.maxBodySize().toBytes(), objectMapper));
        registration.addUrlPatterns(ApiPaths.INGEST);
        return registration;
    }
}

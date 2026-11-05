package io.github.maoz3000.miniwsa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(ContainersConfig.class)
class OpenApiIntegrationTest {

    @Autowired
    private RestTestClient client;

    @Test
    void documentsEveryEndpointOfTheActiveRoles() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("Mini WSA API")
                .jsonPath("$.paths['/v1/events/ingest'].post.responses['201']").exists()
                .jsonPath("$.paths['/v1/events/ingest'].post.responses['413']").exists()
                .jsonPath("$.paths['/v1/events/ingest'].post.requestBody.content['application/json'].schema.oneOf.length()")
                .isEqualTo(2)
                .jsonPath("$.paths['/v1/stats/summary'].get.parameters[?(@.name == 'configId')]").exists()
                .jsonPath("$.paths['/v1/stats/timeseries'].get.parameters[?(@.name == 'interval')].schema.enum[*]")
                .isEqualTo(List.of("1m", "5m", "1h"))
                .jsonPath("$.paths['/v1/events/samples'].get.responses['200'].headers.Link").exists();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/swagger-ui.html"})
    void servesSwaggerUi(String path) {
        // / redirects to /swagger-ui.html, which redirects to /swagger-ui/index.html; the client follows both
        client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(html -> assertThat(html).contains("swagger-ui"));
    }
}

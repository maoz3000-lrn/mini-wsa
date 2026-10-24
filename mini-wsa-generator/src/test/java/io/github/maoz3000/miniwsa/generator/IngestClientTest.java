package io.github.maoz3000.miniwsa.generator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class IngestClientTest {

    private final Queue<String> bodies = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private IntUnaryOperator statusForRequest = n -> 201;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(IngestClient.INGEST_PATH, this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsEveryBatchAsJsonArray() {
        IngestClient.Summary summary = client().send(Batches.of(events(5), 2));

        assertThat(summary).isEqualTo(new IngestClient.Summary(5, 0));
        assertThat(bodies).hasSize(3).allSatisfy(body -> assertThat(body).startsWith("[{\"eventId\":"));
    }

    @Test
    void retriesUnavailableServerAfterRetryAfter() {
        statusForRequest = n -> n == 1 ? 503 : 201;

        assertThat(client().send(List.of(events(3)))).isEqualTo(new IngestClient.Summary(3, 0));
        assertThat(requests).hasValue(2);
    }

    @Test
    void reportsRejectedBatchWithoutRetrying() {
        statusForRequest = n -> 400;

        assertThat(client().send(List.of(events(3)))).isEqualTo(new IngestClient.Summary(0, 1));
        assertThat(requests).hasValue(1);
    }

    private IngestClient client() {
        return new IngestClient(HttpClient.newHttpClient(), JsonMapper.builder().build(),
                URI.create("http://localhost:" + server.getAddress().getPort()), 2);
    }

    private void handle(HttpExchange exchange) throws IOException {
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        int status = statusForRequest.applyAsInt(requests.incrementAndGet());
        exchange.getResponseHeaders().add("Retry-After", "0");
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static List<GeneratedEvent> events(int count) {
        return TrafficGenerator.generate(new TrafficPlan(count, Instant.parse("2026-05-20T10:00:00Z"),
                Duration.ofMinutes(5), 0, 1, 1, 1));
    }
}

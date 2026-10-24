package io.github.maoz3000.miniwsa.generator;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * POSTs batches to {@code /v1/events/ingest} with bounded concurrency. 503 responses are retried after the
 * server's {@code Retry-After}; other failures are reported and not retried.
 */
@Slf4j
final class IngestClient {

    static final String INGEST_PATH = "/v1/events/ingest";
    private static final int MAX_ATTEMPTS = 5;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI ingestUri;
    private final int concurrency;

    IngestClient(HttpClient http, ObjectMapper mapper, URI baseUrl, int concurrency) {
        this.http = http;
        this.mapper = mapper;
        this.ingestUri = baseUrl.resolve(INGEST_PATH);
        this.concurrency = concurrency;
    }

    record Summary(int acceptedEvents, int failedBatches) {
    }

    Summary send(List<List<GeneratedEvent>> batches) {
        Semaphore permits = new Semaphore(concurrency);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> results = batches.stream()
                    .map(batch -> executor.submit(() -> withPermit(permits, () -> post(batch))))
                    .toList();
            List<Integer> accepted = results.stream().map(IngestClient::await).toList();
            return new Summary(accepted.stream().mapToInt(Integer::intValue).sum(),
                    (int) accepted.stream().filter(n -> n == 0).count());
        }
    }

    /** @return number of accepted events, 0 when the batch failed */
    private int post(List<GeneratedEvent> batch) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(ingestUri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(batch)))
                .build();
        return attempt(request, batch.size(), 1);
    }

    private int attempt(HttpRequest request, int size, int attempt) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) {
            return size;
        }
        if (response.statusCode() == 503 && attempt < MAX_ATTEMPTS) {
            long waitSeconds = response.headers().firstValueAsLong("Retry-After").orElse(1);
            log.warn("Ingest unavailable (attempt {}/{}), retrying in {}s", attempt, MAX_ATTEMPTS, waitSeconds);
            Thread.sleep(Duration.ofSeconds(waitSeconds));
            return attempt(request, size, attempt + 1);
        }
        log.error("Batch of {} events rejected with {}: {}", size, response.statusCode(), response.body());
        return 0;
    }

    private interface Call {
        int run() throws IOException, InterruptedException;
    }

    private static int withPermit(Semaphore permits, Call call) throws IOException, InterruptedException {
        permits.acquire();
        try {
            return call.run();
        } finally {
            permits.release();
        }
    }

    private static int await(Future<Integer> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException e) {
            log.error("Batch failed", e.getCause());
            return 0;
        }
    }
}

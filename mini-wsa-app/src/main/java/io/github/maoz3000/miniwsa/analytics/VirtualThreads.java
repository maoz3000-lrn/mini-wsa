package io.github.maoz3000.miniwsa.analytics;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Runs a block that fans independent reads out to virtual threads, and rethrows the first failure unwrapped
 * so callers see the original exception (e.g. a storage failure mapped to 503).
 */
final class VirtualThreads {

    private VirtualThreads() {
    }

    static <R> R fanOut(Function<ExecutorService, R> block) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return block.apply(executor);
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException cause ? cause : e;
        }
    }
}

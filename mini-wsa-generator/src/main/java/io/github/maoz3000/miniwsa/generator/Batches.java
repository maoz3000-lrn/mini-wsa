package io.github.maoz3000.miniwsa.generator;

import java.util.List;
import java.util.stream.IntStream;

final class Batches {

    private Batches() {
    }

    static <T> List<List<T>> of(List<T> items, int size) {
        return IntStream.range(0, (items.size() + size - 1) / size)
                .mapToObj(i -> items.subList(i * size, Math.min(items.size(), (i + 1) * size)))
                .toList();
    }
}

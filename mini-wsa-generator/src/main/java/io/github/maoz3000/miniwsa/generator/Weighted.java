package io.github.maoz3000.miniwsa.generator;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Picks items with probability proportional to their weight.
 */
record Weighted<T>(List<Entry<T>> entries, int totalWeight) {

    record Entry<T>(T value, int weight) {
    }

    @SafeVarargs
    static <T> Weighted<T> of(Entry<T>... entries) {
        List<Entry<T>> list = List.of(entries);
        return new Weighted<>(list, list.stream().mapToInt(Entry::weight).sum());
    }

    static <T> Entry<T> w(T value, int weight) {
        return new Entry<>(value, weight);
    }

    T pick(RandomGenerator random) {
        int ticket = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Entry<T> entry : entries) {
            cumulative += entry.weight();
            if (ticket < cumulative) {
                return entry.value();
            }
        }
        throw new IllegalStateException("weights exhausted");
    }
}

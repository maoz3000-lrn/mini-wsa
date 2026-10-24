package io.github.maoz3000.miniwsa.storage;

/**
 * Trade-off between exact and fast analytics reads.
 */
public enum QueryAccuracy {
    /** Duplicates from re-delivery or client retries are removed at read time (slower). */
    EXACT,
    /** Faster; counts may be briefly inflated until background merges remove duplicates. */
    BEST_EFFORT
}

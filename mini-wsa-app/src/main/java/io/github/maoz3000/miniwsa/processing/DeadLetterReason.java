package io.github.maoz3000.miniwsa.processing;

public enum DeadLetterReason {
    /** The Kafka record could not be read as an event. */
    DESERIALIZATION,
    /** The event failed the pre-insert checks of the store. */
    ROW_VALIDATION,
    /** The store rejected the whole batch for data reasons. */
    STORAGE_REJECTED
}

package io.github.maoz3000.miniwsa.config;

/**
 * A part of the pipeline a process can run. One image serves every role; {@code miniwsa.roles} picks which.
 */
public enum Role {
    /** {@code POST /v1/events/ingest} and the Kafka producer. */
    INGEST,
    /** Kafka consumer, enrichment, Redis window and ClickHouse writes. */
    PROCESSING,
    /** Stats and samples endpoints reading from ClickHouse. */
    ANALYTICS
}

package io.github.maoz3000.miniwsa.processing;

/**
 * A record that processing could not store, with where it came from and why.
 *
 * @param value the original record value bytes
 */
public record DeadLetter(String key, byte[] value, DeadLetterReason reason, String error,
                         String sourceTopic, int sourcePartition, long sourceOffset) {
}

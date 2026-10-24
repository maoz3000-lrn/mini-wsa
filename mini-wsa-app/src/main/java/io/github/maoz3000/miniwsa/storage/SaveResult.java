package io.github.maoz3000.miniwsa.storage;

import java.util.List;

/**
 * @param rejected events refused individually for bad data; every other event of the batch was stored
 */
public record SaveResult(List<RejectedEvent> rejected) {

    public SaveResult {
        rejected = List.copyOf(rejected);
    }
}

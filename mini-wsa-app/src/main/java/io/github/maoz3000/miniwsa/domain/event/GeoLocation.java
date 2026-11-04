package io.github.maoz3000.miniwsa.domain.event;

import lombok.Builder;

@Builder(toBuilder = true)
public record GeoLocation(
        String country,
        String city) {
}

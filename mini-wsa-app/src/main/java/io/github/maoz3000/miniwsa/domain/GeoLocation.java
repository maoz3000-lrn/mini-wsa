package io.github.maoz3000.miniwsa.domain;

import lombok.Builder;

import jakarta.validation.constraints.Size;

@Builder(toBuilder = true)
public record GeoLocation(
        @Size(max = 2) String country,
        @Size(max = 128) String city) {
}

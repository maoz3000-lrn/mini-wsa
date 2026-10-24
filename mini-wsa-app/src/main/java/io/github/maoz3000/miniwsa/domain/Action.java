package io.github.maoz3000.miniwsa.domain;

/**
 * Action the edge took on the request, with its contribution to the threat score.
 */
public enum Action {
    DENY(20),
    ALERT(10),
    MONITOR(0);

    private final int points;

    Action(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }
}

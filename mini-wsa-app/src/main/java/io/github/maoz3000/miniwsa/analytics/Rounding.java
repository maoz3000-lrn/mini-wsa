package io.github.maoz3000.miniwsa.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Rounding {

    private Rounding() {
    }

    static double oneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

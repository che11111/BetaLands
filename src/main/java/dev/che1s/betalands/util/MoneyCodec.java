package dev.che1s.betalands.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stores monetary values as integer cents in persistent formats.
 */
public final class MoneyCodec {
    private MoneyCodec() {
    }

    public static long toCents(double amount) {
        return BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValue();
    }

    public static double fromCents(long cents) {
        return BigDecimal.valueOf(cents)
                .movePointLeft(2).doubleValue();
    }
}

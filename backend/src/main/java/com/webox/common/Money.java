package com.webox.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is transported and computed as integer cents everywhere (API <-> frontend).
 * Only at the persistence boundary do we convert to DECIMAL(10,2).
 * This removes all floating-point rounding issues (PRD 3.2 / A.1 price precision).
 */
public final class Money {
    private Money() {}

    public static BigDecimal toDecimal(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public static long toCents(BigDecimal value) {
        if (value == null) return 0L;
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long price(long unitCents, int quantity) {
        return Math.multiplyExact(unitCents, (long) quantity);
    }

    public static long sum(long... values) {
        long total = 0L;
        for (long v : values) total = Math.addExact(total, v);
        return total;
    }

    public static String format(long cents) {
        return "¥" + toDecimal(cents).toPlainString();
    }
}

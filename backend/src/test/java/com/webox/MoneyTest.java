package com.webox;

import com.webox.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void integerCentsAvoidFloatingPointDrift() {
        long unit = 2250L; // ¥22.50
        assertEquals(6750L, Money.price(unit, 3));
        assertEquals(9000L, Money.sum(2250L, 2250L, 2250L, 2250L));
        assertEquals("¥22.50", Money.format(unit));
        assertEquals(new BigDecimal("22.50"), Money.toDecimal(unit));
    }

    @Test
    void decimalConversionIsExactToTheCent() {
        assertEquals(2850L, Money.toCents(new BigDecimal("28.50")));
        assertEquals(1800L, Money.toCents(new BigDecimal("18.00")));
        assertEquals(0L, Money.toCents(null));
    }

    @Test
    void addOnsAreAddedWithoutRoundingErrors() {
        // Burger ¥38.00 + Cheese ¥3.00 + Bacon ¥5.00 = ¥46.00
        long withOptions = Math.addExact(3800L, Math.addExact(300L, 500L));
        assertEquals(4600L, withOptions);
        assertEquals("¥46.00", Money.format(withOptions));
    }
}

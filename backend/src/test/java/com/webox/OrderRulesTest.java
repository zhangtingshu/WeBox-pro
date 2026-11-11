package com.webox;

import com.webox.service.OrderRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Test
    void lunchBeforeCutoffStaysLunch() {
        OrderRules.Resolved r = OrderRules.resolve(TODAY, LocalTime.of(9, 45), TODAY, "Lunch");
        assertEquals(TODAY, r.deliveryDate());
        assertEquals("Lunch", r.mealPeriod());
        assertEquals("", r.notice());
    }

    @Test
    void lunchAfterTenSwitchesToSameDayDinner() {
        OrderRules.Resolved r = OrderRules.resolve(TODAY, LocalTime.of(10, 30), TODAY, "Lunch");
        assertEquals(TODAY, r.deliveryDate());
        assertEquals("Dinner", r.mealPeriod());
        assertTrue(r.notice().contains("10:00"));
    }

    @Test
    void afterBothCutoffsSwitchesToNextDayLunch() {
        OrderRules.Resolved r = OrderRules.resolve(TODAY, LocalTime.of(16, 0), TODAY, "Lunch");
        assertEquals(TODAY.plusDays(1), r.deliveryDate());
        assertEquals("Lunch", r.mealPeriod());

        OrderRules.Resolved dinner = OrderRules.resolve(TODAY, LocalTime.of(15, 0), TODAY, "Dinner");
        assertEquals(TODAY.plusDays(1), dinner.deliveryDate());
        assertEquals("Lunch", dinner.mealPeriod());
    }

    @Test
    void futureDeliveryDateIsNotAffectedByTodayCutoffs() {
        LocalDate tomorrow = TODAY.plusDays(1);
        OrderRules.Resolved r = OrderRules.resolve(TODAY, LocalTime.of(16, 0), tomorrow, "Dinner");
        assertEquals(tomorrow, r.deliveryDate());
        assertEquals("Dinner", r.mealPeriod());
        assertEquals("", r.notice());
    }

    @Test
    void portionLimitCountsTotalPortionsNotVarieties() {
        assertFalse(OrderRules.exceedsPortionLimit(5));
        assertTrue(OrderRules.exceedsPortionLimit(6)); // 3 + 3 of two dishes
        assertTrue(OrderRules.portionLimitMessage().contains("5"));
    }

    @Test
    void defaultMealPeriodFollowsTheClock() {
        assertEquals("Lunch", OrderRules.defaultMealPeriod(LocalTime.of(9, 0)));
        assertEquals("Dinner", OrderRules.defaultMealPeriod(LocalTime.of(12, 0)));
        assertEquals("Lunch", OrderRules.normalizePeriod("NotAPeriod"));
    }
}

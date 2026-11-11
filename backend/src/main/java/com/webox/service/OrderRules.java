package com.webox.service;

import com.webox.common.Constants;

import java.time.LocalDate;
import java.time.LocalTime;

/** Pure business rules for ordering windows and portion limits (unit-testable, no Spring). */
public final class OrderRules {
    private OrderRules() {}

    public record Resolved(LocalDate deliveryDate, String mealPeriod, String notice) {}

    public static String normalizePeriod(String period) {
        return Constants.MEAL_PERIOD_SET.contains(period) ? period : "Lunch";
    }

    /** Which meal is still orderable right now. */
    public static String defaultMealPeriod(LocalTime now) {
        if (now.isBefore(Constants.LUNCH_CUTOFF)) return "Lunch";
        return now.isBefore(Constants.DINNER_CUTOFF) ? "Dinner" : "Lunch";
    }

    /**
     * Lunch closes at 10:00, Dinner at 15:00. After a cutoff the order automatically
     * moves to the nearest orderable meal: today's Dinner, else tomorrow's Lunch.
     */
    public static Resolved resolve(LocalDate today, LocalTime now,
                                   LocalDate requestedDate, String requestedPeriod) {
        String period = normalizePeriod(requestedPeriod);
        LocalDate date = requestedDate == null ? today : requestedDate;

        // Future dates have no same-day cutoff restriction.
        if (date.isAfter(today)) return new Resolved(date, period, "");
        // A past date can never be ordered; clamp it to today.
        if (date.isBefore(today)) date = today;

        if ("Lunch".equals(period) && !now.isBefore(Constants.LUNCH_CUTOFF)) {
            if (now.isBefore(Constants.DINNER_CUTOFF)) {
                return new Resolved(today, "Dinner",
                        "Lunch ordering closes at 10:00. Your order was moved to today's Dinner.");
            }
            return new Resolved(today.plusDays(1), "Lunch",
                    "Today's Lunch and Dinner windows are both closed. "
                            + "Your order was moved to tomorrow's Lunch.");
        }
        if ("Dinner".equals(period) && !now.isBefore(Constants.DINNER_CUTOFF)) {
            return new Resolved(today.plusDays(1), "Lunch",
                    "Dinner ordering closes at 15:00. Your order was moved to tomorrow's Lunch.");
        }
        return new Resolved(date, period, "");
    }

    public static boolean exceedsPortionLimit(int totalPortions) {
        return totalPortions > Constants.MAX_PORTIONS_PER_ORDER;
    }

    public static String portionLimitMessage() {
        return "An order can contain at most " + Constants.MAX_PORTIONS_PER_ORDER
                + " portions in total (all dishes combined).";
    }
}

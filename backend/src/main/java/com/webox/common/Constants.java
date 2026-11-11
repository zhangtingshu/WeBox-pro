package com.webox.common;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Business enums and ordering rules, all in English (PRD section 6). */
public final class Constants {
    private Constants() {}

    public static final List<String> CATEGORIES = List.of(
            "Chinese", "Western", "Japanese", "Light Meal", "Korean", "Southeast Asian");
    public static final Set<String> CATEGORY_SET = new LinkedHashSet<>(CATEGORIES);

    public static final List<String> ALLERGENS = List.of(
            "Peanuts", "Dairy", "Egg", "Gluten", "Soy", "Fish", "Shellfish");
    public static final Set<String> ALLERGEN_SET = new LinkedHashSet<>(ALLERGENS);

    public static final List<String> SPICE_LEVELS = List.of("None", "Mild", "Medium", "Hot");
    public static final Set<String> SPICE_SET = new LinkedHashSet<>(SPICE_LEVELS);

    public static final List<String> TASTE_LEVELS = List.of("Light", "Balanced", "Rich");
    public static final Set<String> TASTE_SET = new LinkedHashSet<>(TASTE_LEVELS);

    public static final List<String> MEAL_PERIODS = List.of("Lunch", "Dinner");
    public static final Set<String> MEAL_PERIOD_SET = new LinkedHashSet<>(MEAL_PERIODS);

    public static final List<String> ORDER_STATUSES =
            List.of("Pending", "Confirmed", "Completed", "Cancelled");
    public static final List<String> ACTIVE_STATUSES = List.of("Pending", "Confirmed");

    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final int MAX_PORTIONS_PER_ORDER = 5;
    public static final int LOW_STOCK_THRESHOLD = 3;
    public static final int MAX_SEARCH_LENGTH = 50;
    public static final int MAX_ADDRESS_LENGTH = 200;
    public static final int MAX_EMAIL_LENGTH = 200;
    public static final int MAX_PAGE_SIZE = 48;

    public static final LocalTime LUNCH_CUTOFF = LocalTime.of(10, 0);
    public static final LocalTime DINNER_CUTOFF = LocalTime.of(15, 0);
}

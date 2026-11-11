package com.webox.service;

import java.util.Collection;
import java.util.List;

/** Allergen warnings are advisory: flagged dishes are highlighted, never filtered out. */
public final class AllergenMatcher {
    private AllergenMatcher() {}

    public static List<String> conflicts(Collection<String> dishAllergens,
                                         Collection<String> flaggedAllergens) {
        if (dishAllergens == null || flaggedAllergens == null) return List.of();
        return dishAllergens.stream()
                .filter(flaggedAllergens::contains)
                .distinct()
                .toList();
    }

    public static String warning(List<String> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) return "";
        return "This dish contains an allergen you flagged: [" + String.join(", ", conflicts)
                + "]. Add anyway?";
    }
}

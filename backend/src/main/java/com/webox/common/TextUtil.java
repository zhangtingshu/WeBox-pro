package com.webox.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Input-boundary helpers: length caps, LIKE escaping, CSV enum encoding. */
public final class TextUtil {
    private TextUtil() {}

    public static String clean(String value, int maxLength) {
        if (value == null) return "";
        String v = value.trim();
        // Strip control characters; keep quotes/apostrophes (parameter binding keeps them safe).
        v = v.replaceAll("[\\p{Cntrl}]", " ");
        return v.length() > maxLength ? v.substring(0, maxLength) : v;
    }

    /** Escape LIKE wildcards so a search keyword cannot act as a pattern. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static String joinCsv(Collection<String> values) {
        if (values == null) return "";
        return String.join(",", new LinkedHashSet<>(values));
    }

    /** Whitelist enum values: never trust client-supplied free text for enums. */
    public static List<String> whitelist(Collection<String> values, Set<String> allowed) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(allowed::contains).distinct().toList();
    }
}

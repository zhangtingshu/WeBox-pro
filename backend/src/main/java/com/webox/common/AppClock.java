package com.webox.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Single source of truth for business time (canteen operates in Asia/Shanghai). */
public final class AppClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppClock() {}

    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static LocalDateTime now() { return LocalDateTime.now(ZONE); }
    public static LocalTime time() { return LocalTime.now(ZONE); }
}

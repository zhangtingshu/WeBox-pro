package com.webox;

import com.webox.config.AppProperties;
import com.webox.service.AllergenMatcher;
import com.webox.service.MenuCache;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheAndAllergenTest {

    @Test
    void menuCacheHonoursTtlAndWriteInvalidation() throws InterruptedException {
        AppProperties props = new AppProperties();
        props.setMenuCacheTtlSeconds(1);
        MenuCache cache = new MenuCache(props);
        cache.put("menu|today", "page-1");
        assertEquals("page-1", cache.get("menu|today"));
        assertEquals(1L, cache.hits());

        Thread.sleep(1100L);
        assertNull(cache.get("menu|today"), "expired entries must not be served");

        cache.put("menu|today", "page-2");
        cache.invalidateAll(); // a write (order / dish edit) must drop cached pages
        assertNull(cache.get("menu|today"));
        assertEquals(0, cache.size());
    }

    @Test
    void flaggedAllergensProduceAnAdvisoryWarningNotAFilter() {
        List<String> conflicts = AllergenMatcher.conflicts(
                List.of("Dairy", "Egg"), List.of("Peanuts", "Egg"));
        assertEquals(List.of("Egg"), conflicts);
        assertEquals("This dish contains an allergen you flagged: [Egg]. Add anyway?",
                AllergenMatcher.warning(conflicts));
        assertTrue(AllergenMatcher.warning(AllergenMatcher.conflicts(
                List.of("Gluten"), List.of("Peanuts"))).isEmpty());
    }
}

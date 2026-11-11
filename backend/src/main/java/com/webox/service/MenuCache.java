package com.webox.service;

import com.webox.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process hot-menu cache (Redis is optional per PRD 7.1).
 * - TTL bounds staleness during the 09:30-10:00 peak.
 * - Every write path (dish edit, daily menu edit, order, cancellation) calls invalidateAll(),
 *   giving read-through + write-invalidate consistency for stock numbers.
 */
@Component
public class MenuCache {
    private record Entry(Object value, long expiresAtMillis) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final long ttlMillis;

    public MenuCache(AppProperties props) {
        this.ttlMillis = props.getMenuCacheTtlSeconds() * 1000L;
    }

    public Object get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            store.remove(key, entry);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value();
    }

    public void put(String key, Object value) {
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    public void invalidateAll() { store.clear(); }

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public int size() { return store.size(); }
    public long ttlSeconds() { return ttlMillis / 1000L; }
}

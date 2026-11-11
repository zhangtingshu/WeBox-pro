package com.webox.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured single-line JSON logs. Callers must never pass passwords or tokens. */
public final class JsonLog {
    private static final Logger LOG = LoggerFactory.getLogger("webox.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonLog() {}

    public static void event(String level, String event, Object... keyValues) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", Instant.now().toString());
        line.put("level", level);
        line.put("event", event);
        line.put("requestId", RequestContext.requestId());
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            line.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        try {
            LOG.info(MAPPER.writeValueAsString(line));
        } catch (Exception e) {
            LOG.info("{}", line);
        }
    }

    public static void info(String event, Object... kv) { event("INFO", event, kv); }
    public static void warn(String event, Object... kv) { event("WARN", event, kv); }
    public static void error(String event, Object... kv) { event("ERROR", event, kv); }
}

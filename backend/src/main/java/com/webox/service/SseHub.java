package com.webox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webox.dto.Dtos.StockEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Broadcasts stock changes to every open menu tab; heartbeats keep proxies from closing SSE. */
@Component
public class SseHub {
    private static final long TIMEOUT_MILLIS = 300_000L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(mapper.writeValueAsString(Map.of("message", "Stock updates connected."))));
        } catch (Exception ignored) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(List<StockEvent> events) {
        if (events == null || events.isEmpty()) return;
        String payload;
        try {
            payload = mapper.writeValueAsString(events);
        } catch (Exception e) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("stock").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** Console dish/menu edits should make every open menu tab re-fetch. */
    public void broadcastRefresh() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("refresh").data("{}"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public int subscriberCount() { return emitters.size(); }
}

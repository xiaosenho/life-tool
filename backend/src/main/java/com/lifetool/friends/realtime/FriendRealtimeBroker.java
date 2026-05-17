package com.lifetool.friends.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class FriendRealtimeBroker {
    private static final Logger log = LoggerFactory.getLogger(FriendRealtimeBroker.class);
    private static final long SSE_TIMEOUT_MS = 0L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByUserId.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        sendRaw(emitter, "connected", Map.of("connectedAt", Instant.now().toString()));
        return emitter;
    }

    public boolean hasActiveSubscriber(String userId) {
        return !emittersByUserId.getOrDefault(userId, new CopyOnWriteArrayList<>()).isEmpty();
    }

    public void publish(String userId, FriendEventType type, Object payload) {
        FriendRealtimeEvent event = new FriendRealtimeEvent(
                UUID.randomUUID().toString(),
                type,
                userId,
                Instant.now(),
                payload
        );
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.id())
                        .name(type.name().toLowerCase())
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                removeEmitter(userId, emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 25000L)
    public void heartbeat() {
        emittersByUserId.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                sendRaw(emitter, "ping", Map.of("ts", Instant.now().toString()));
            }
            if (emitters.isEmpty()) {
                emittersByUserId.remove(userId);
            }
        });
    }

    private void sendRaw(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            log.debug("SSE initial send failed: {}", ex.getMessage());
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUserId.remove(userId);
        }
    }
}

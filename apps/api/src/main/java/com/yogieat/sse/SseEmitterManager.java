package com.yogieat.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class SseEmitterManager {

    private static final long SSE_TIMEOUT = 60_000L * 5; // 5분

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String accessKey) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        List<SseEmitter> accessKeyEmitters = emitters.computeIfAbsent(
                accessKey, k -> new CopyOnWriteArrayList<>()
        );
        accessKeyEmitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(accessKey, emitter));
        emitter.onTimeout(() -> removeEmitter(accessKey, emitter));
        emitter.onError(e -> removeEmitter(accessKey, emitter));

        log.info("SSE subscription registered - accessKey: {}, current subscribers: {}", accessKey, accessKeyEmitters.size());
        return emitter;
    }

    public void sendToEmitter(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event");
        }
    }

    public void send(String accessKey, String eventName, Object data) {
        List<SseEmitter> accessKeyEmitters = emitters.get(accessKey);
        if (accessKeyEmitters == null || accessKeyEmitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : accessKeyEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.warn("Failed to send SSE event - accessKey: {}", accessKey);
                deadEmitters.add(emitter);
            }
        }

        deadEmitters.forEach(emitter -> removeEmitter(accessKey, emitter));
    }

    public void complete(String accessKey) {
        List<SseEmitter> accessKeyEmitters = emitters.remove(accessKey);
        if (accessKeyEmitters != null) {
            accessKeyEmitters.forEach(SseEmitter::complete);
            log.info("SSE connection closed - accessKey: {}", accessKey);
        }
    }

    private void removeEmitter(String accessKey, SseEmitter emitter) {
        List<SseEmitter> accessKeyEmitters = emitters.get(accessKey);
        if (accessKeyEmitters != null) {
            accessKeyEmitters.remove(emitter);
            if (accessKeyEmitters.isEmpty()) {
                emitters.remove(accessKey);
            }
        }
    }
}

package com.yogieat.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class SseEmitterManager {

    private static final long SSE_TIMEOUT = 60_000L * 60; // 1시간 (heartbeat가 연결 유지 담당)

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

    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        emitters.forEach((accessKey, accessKeyEmitters) -> {
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : accessKeyEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data("ping"));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }

            if (!deadEmitters.isEmpty()) {
                log.info("Cleaning up {} dead connection(s) for accessKey: {}",
                        deadEmitters.size(), accessKey);
            }

            deadEmitters.forEach(emitter -> removeEmitter(accessKey, emitter));
        });

        if (!emitters.isEmpty()) {
            log.debug("SSE heartbeat sent - active accessKeys: {}", emitters.size());
        }
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
            boolean removed = accessKeyEmitters.remove(emitter);

            if (!removed) {
                return;
            }

            log.info("Removed emitter - accessKey: {}, remaining: {}",
                    accessKey, accessKeyEmitters.size());

            if (accessKeyEmitters.isEmpty()) {
                emitters.remove(accessKey);
                log.info("All connections closed for accessKey: {}", accessKey);
            }
        }
    }
}

package com.cakemate.cake_platform.domain.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public interface EmitterRepository {
    void deleteAllEmitterStartWithId(String emitterId);
    void deleteById(String id);
    void save(String emitterId, SseEmitter emitter);

    Optional<SseEmitter> findById(String emitterId);

}
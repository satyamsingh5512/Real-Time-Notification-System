package com.uber.notification.application.port;

import java.time.Duration;
import java.util.Optional;

/** Output port for distributed caching (Redis in production, in-memory for tests). */
public interface CachePort {

    void put(String key, String value, Duration ttl);

    Optional<String> get(String key);

    void evict(String key);

    /** Atomic increment used for rate limiting / counters (e.g. unread count cache). */
    long increment(String key, long delta, Duration ttl);
}

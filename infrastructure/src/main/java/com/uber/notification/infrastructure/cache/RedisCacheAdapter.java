package com.uber.notification.infrastructure.cache;

import com.uber.notification.application.port.CachePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed implementation of the application's CachePort. Used to cache hot-path reads
 * (e.g. rendered template lookups, unread counts) and as an atomic counter store for the
 * unread-badge count so the REST API and WebSocket gateway don't hit Postgres on every poll.
 */
@Component
public class RedisCacheAdapter implements CachePort {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public long increment(String key, long delta, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        redisTemplate.expire(key, ttl);
        return value != null ? value : delta;
    }
}

package com.uber.notification.infrastructure.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Redis Sorted-Set (ZSET) delay queue for retries — the scalable alternative to
 * blocking Kafka consumer threads.
 *
 * <p>Members are notification IDs scored by their {@code notBefore} epoch-millis.
 * A lightweight scheduler polls {@code ZRANGEBYSCORE ... LIMIT 0 N} for due entries,
 * removing each atomically (ZREM) before redelivery so at-most-once dequeue holds
 * even with multiple poller pods. Kafka bucketed topics remain the default path;
 * this queue is available for deployments that prefer Redis-native delays or need
 * sub-second precision without extra topics.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisRetryDelayQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisRetryDelayQueue.class);
    static final String KEY = "notif:retry:delay-queue";

    private final StringRedisTemplate redisTemplate;

    public RedisRetryDelayQueue(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void schedule(java.util.UUID notificationId, Instant notBefore) {
        redisTemplate.opsForZSet().add(KEY, notificationId.toString(), notBefore.toEpochMilli());
        log.debug("ZSET-delayed notification {} until {}", notificationId, notBefore);
    }

    /** Atomically claim up to {@code limit} due notification IDs (removes them from the set). */
    public Set<String> claimDue(int limit) {
        long now = Instant.now().toEpochMilli();
        Set<String> due = redisTemplate.opsForZSet().rangeByScore(KEY, 0, now, 0, limit);
        if (due == null || due.isEmpty()) {
            return Set.of();
        }
        for (String id : due) {
            redisTemplate.opsForZSet().remove(KEY, id);
        }
        return due;
    }

    public long pendingCount() {
        Long size = redisTemplate.opsForZSet().size(KEY);
        return size == null ? 0 : size;
    }
}

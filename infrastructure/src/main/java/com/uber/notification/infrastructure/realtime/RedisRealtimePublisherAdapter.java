package com.uber.notification.infrastructure.realtime;

import com.uber.notification.application.port.RealtimePublisherPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes real-time in-app/WebSocket notification payloads over Redis Pub/Sub so that
 * ANY API pod holding the target user's live WebSocket session (not necessarily the pod
 * that processed the Kafka message) can push it down the socket. This decouples notification
 * processing from WebSocket session affinity, which is essential once the API is horizontally
 * scaled behind a load balancer with no sticky sessions.
 */
@Component
public class RedisRealtimePublisherAdapter implements RealtimePublisherPort {

    private static final String CHANNEL_PREFIX = "notif:realtime:";

    private final StringRedisTemplate redisTemplate;

    public RedisRealtimePublisherAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publishToUser(String userId, String jsonPayload) {
        redisTemplate.convertAndSend(CHANNEL_PREFIX + userId, jsonPayload);
    }

    public static String channelForUser(String userId) {
        return CHANNEL_PREFIX + userId;
    }
}

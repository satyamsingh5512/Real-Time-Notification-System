package com.uber.notification.infrastructure.realtime;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Bridges Redis Pub/Sub messages into the local WebSocket session registry. Registered
 * against a pattern topic ("notif:realtime:*") in RedisConfig so every pod receives every
 * user's message and simply no-ops if it doesn't hold that user's session.
 */
@Component
public class RedisRealtimeSubscriber implements MessageListener {

    private final WebSocketSessionRegistry sessionRegistry;

    public RedisRealtimeSubscriber(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String userId = channel.substring(channel.lastIndexOf(':') + 1);
        String payload = new String(message.getBody());
        sessionRegistry.sendToUser(userId, payload);
    }
}

package com.uber.notification.infrastructure.realtime;

import com.uber.notification.domain.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bridges Redis Pub/Sub frames to the local WebSocket sessions of the target user.
 * Every payload is wrapped in a typed envelope ({@code {"type":"notification",...}})
 * so clients can switch on {@code type} instead of sniffing fields.
 */
@Component
public class RedisRealtimeSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeSubscriber.class);
    private static final String USER_CHANNEL_PREFIX = "notif:realtime:";
    private static final String NOTIFICATION_TYPE = "notification";

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectProvider<NotificationRepository> notificationRepository;

    public RedisRealtimeSubscriber(WebSocketSessionRegistry sessionRegistry) {
        this(sessionRegistry, (ObjectProvider<NotificationRepository>) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RedisRealtimeSubscriber(WebSocketSessionRegistry sessionRegistry,
                                   ObjectProvider<NotificationRepository> notificationRepository) {
        this.sessionRegistry = sessionRegistry;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), java.nio.charset.StandardCharsets.UTF_8);
        if (!channel.startsWith(USER_CHANNEL_PREFIX)) {
            return;
        }
        String userId = channel.substring(USER_CHANNEL_PREFIX.length());
        String payload = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);

        if (!sessionRegistry.hasLocalSession(userId)) {
            return; // user is connected to a different pod
        }
        try {
            sessionRegistry.sendToUser(userId, envelope(payload));
            pushUnreadCount(userId);
        } catch (Exception e) {
            log.warn("Failed to deliver realtime frame to user {}: {}", userId, e.getMessage());
        }
    }

    /** Wraps a provider payload in the typed envelope the client expects. */
    private String envelope(String payload) {
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") && trimmed.contains("\"type\"")) {
            return trimmed; // already typed by a structured producer
        }
        return "{\"type\":\"" + NOTIFICATION_TYPE + "\",\"" + NOTIFICATION_TYPE + "\":" + trimmed + "}";
    }

    /** Pushes the current unread count so the bell updates without a page refresh. */
    private void pushUnreadCount(String userId) {
        if (notificationRepository == null) {
            return;
        }
        NotificationRepository repo = notificationRepository.getIfAvailable();
        if (repo == null) {
            return;
        }
        try {
            long unread = repo.countUnread(UUID.fromString(userId));
            sessionRegistry.sendToUser(userId, "{\"type\":\"unread_count\",\"count\":" + unread + "}");
        } catch (IllegalArgumentException ignored) {
            // non-UUID channel key: nothing to count
        }
    }
}
package com.uber.notification.infrastructure.realtime;

import com.uber.notification.infrastructure.metrics.NotificationMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of live WebSocket sessions on this pod, keyed by userId (a user may
 * have multiple sessions open, e.g. multiple browser tabs or devices). This is intentionally
 * per-pod local state; cross-pod fanout is handled by Redis Pub/Sub (see
 * {@link RedisRealtimeSubscriber}), so no distributed session store is required.
 */
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final NotificationMetrics notificationMetrics;

    public WebSocketSessionRegistry(@Autowired(required = false) NotificationMetrics notificationMetrics) {
        this.notificationMetrics = notificationMetrics;
    }

    public void register(String userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        if (notificationMetrics != null) {
            notificationMetrics.onSessionOpened();
        }
    }

    public void unregister(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions != null) {
            if (sessions.remove(session) && notificationMetrics != null) {
                notificationMetrics.onSessionClosed();
            }
            if (sessions.isEmpty()) {
                sessionsByUserId.remove(userId);
            }
        }
    }

    public void sendToUser(String userId, String payload) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                // Best-effort delivery: a broken session will be cleaned up on its close event.
            }
        }
    }

    public boolean hasLocalSession(String userId) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int activeSessionCount() {
        return sessionsByUserId.values().stream().mapToInt(Set::size).sum();
    }
}

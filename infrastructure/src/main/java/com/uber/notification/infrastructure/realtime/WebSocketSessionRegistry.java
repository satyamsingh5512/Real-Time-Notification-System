package com.uber.notification.infrastructure.realtime;

import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.infrastructure.metrics.NotificationMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory registry of live WebSocket sessions on this pod, keyed by userId (a user may
 * have multiple sessions open, e.g. multiple browser tabs or devices). This is intentionally
 * per-pod local state; cross-pod fanout is handled by Redis Pub/Sub (see
 * {@link RedisRealtimeSubscriber}), so no distributed session store is required.
 *
 * <p>Sends are serialized per session — Spring's {@code WebSocketSession} is not
 * thread-safe and concurrent sends (Redis subscriber + heartbeat + HTTP-driven push)
 * otherwise corrupt frames. Dead sessions are evicted eagerly on send failure so
 * the map cannot leak.
 */
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final NotificationMetrics notificationMetrics;
    private final ObjectProvider<NotificationRepository> notificationRepository;

    /** Test-friendly overload: no unread-count repository. */
    public WebSocketSessionRegistry(NotificationMetrics notificationMetrics) {
        this.notificationMetrics = notificationMetrics;
        this.notificationRepository = null;
    }

    /**
     * Production constructor. Both dependencies are resolved through {@code ObjectProvider}
     * so they stay optional (absent in @WebMvcTest slices) and Spring always has exactly one
     * unambiguous autowiring candidate.
     */
    @Autowired
    public WebSocketSessionRegistry(ObjectProvider<NotificationMetrics> metricsProvider,
                                    ObjectProvider<NotificationRepository> repositoryProvider) {
        this(metricsProvider != null ? metricsProvider.getIfAvailable() : null, repositoryProvider);
    }

    private WebSocketSessionRegistry(NotificationMetrics notificationMetrics,
                                     ObjectProvider<NotificationRepository> notificationRepository) {
        this.notificationMetrics = notificationMetrics;
        this.notificationRepository = notificationRepository;
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
                sessionsByUserId.remove(userId, sessions);
            }
        }
    }

    public void sendToUser(String userId, String payload) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                if (notificationMetrics != null) {
                    notificationMetrics.onSessionClosed();
                }
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                // Best-effort delivery: evict the broken session so it cannot leak.
                sessions.remove(session);
                if (notificationMetrics != null) {
                    notificationMetrics.onSessionClosed();
                }
            }
        }
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(userId, sessions);
        }
    }

    /** Live unread count for the bell, read through the indexed count query. */
    public long unreadCount(String userId) {
        NotificationRepository repo = notificationRepository == null ? null : notificationRepository.getIfAvailable();
        if (repo == null) {
            return 0;
        }
        try {
            return repo.countUnread(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    public boolean hasLocalSession(String userId) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int activeSessionCount() {
        return sessionsByUserId.values().stream().mapToInt(Set::size).sum();
    }

    /** Visits every live session on this pod (used by the heartbeat sweep). */
    public void forEachSession(Consumer<WebSocketSession> action) {
        sessionsByUserId.values().forEach(sessions -> sessions.forEach(action));
    }
}
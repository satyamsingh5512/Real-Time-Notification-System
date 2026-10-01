package com.uber.notification.api.websocket;

import com.uber.notification.infrastructure.realtime.WebSocketSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Real-time in-app notification endpoint (ws://host/ws/notifications?token=...).
 *
 * <p>Protocol (structured frames rather than a bare payload, so clients can react
 * without string sniffing):
 * <ul>
 *   <li>server → client {@code {"type":"notification", ...}} — a new notification</li>
 *   <li>server → client {@code {"type":"unread_count","count":N}} — bell refresh</li>
 *   <li>server → client {@code {"type":"connected","userId":"..."}} — handshake ack</li>
 *   <li>server → client WebSocket ping frames every 30s — keeps nginx/LB from
 *       reaping idle connections and detects dead sockets</li>
 *   <li>client → server {@code {"type":"ping"}} — answered with {@code {"type":"pong"}}</li>
 *   <li>client → server {@code {"type":"unread_request"}} — triggers a count refresh</li>
 * </ul>
 * Everything else sent by the client is ignored: the channel is server-push only.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    private final WebSocketSessionRegistry sessionRegistry;
    private final java.util.concurrent.ScheduledExecutorService heartbeat =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public NotificationWebSocketHandler(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.heartbeat.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("unauthenticated"));
            } catch (Exception ignored) {
                // session already gone
            }
            return;
        }
        sessionRegistry.register(userId, session);
        send(session, "{\"type\":\"connected\",\"userId\":\"" + userId + "\"}");
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String payload = message.getPayload();
        if (payload.contains("\"ping\"")) {
            send(session, "{\"type\":\"pong\"}");
        } else if (payload.contains("\"unread_request\"")) {
            send(session, "{\"type\":\"unread_count\",\"count\":" + unreadCountFor(session) + "}");
        }
        // Any other client message is ignored: this channel is server-push only.
    }

    private long unreadCountFor(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            return 0;
        }
        return sessionRegistry.unreadCount(userId);
    }

    private void sendHeartbeats() {
        try {
            sessionRegistry.forEachSession(session -> {
                if (!session.isOpen()) {
                    return;
                }
                try {
                    session.sendMessage(new PingMessage());
                } catch (Exception e) {
                    log.debug("heartbeat failed for session {}: {}", session.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("heartbeat sweep failed: {}", e.getMessage());
        }
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("failed to push frame to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
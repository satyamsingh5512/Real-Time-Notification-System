package com.uber.notification.api.websocket;

import com.uber.notification.infrastructure.realtime.WebSocketSessionRegistry;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Real-time in-app notification endpoint (ws://host/ws/notifications?token=...). Purely a
 * push channel from server to client: incoming client messages are not used for delivery,
 * only for optional ping/pong keep-alive.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;

    public NotificationWebSocketHandler(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessionRegistry.register(userId, session);
        }
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
        // Client -> server messages are not part of the notification delivery contract;
        // reserved for future keep-alive/ack handling.
    }
}

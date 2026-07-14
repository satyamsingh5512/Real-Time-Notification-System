package com.uber.notification.api.websocket;

import com.uber.notification.api.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Authenticates the WebSocket handshake via a `token` query parameter (browsers cannot set
 * custom headers on the native WebSocket handshake, so the JWT travels as a query param here,
 * over TLS in production). On success, the resolved userId is attached to the session
 * attributes for {@link NotificationWebSocketHandler} to register against.
 */
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                    @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            var principal = jwtService.parseToken(token);
            attributes.put("userId", principal.userId().toString());
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: invalid token");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                @NonNull WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        return List.of(query.split("&")).stream()
                .filter(p -> p.startsWith("token="))
                .map(p -> p.substring("token=".length()))
                .findFirst()
                .orElse(null);
    }
}

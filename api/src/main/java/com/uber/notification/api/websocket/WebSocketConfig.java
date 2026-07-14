package com.uber.notification.api.websocket;

import com.uber.notification.api.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtService jwtService;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler, JwtService jwtService) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.jwtService = jwtService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(new WebSocketAuthInterceptor(jwtService))
                .setAllowedOriginPatterns("*");
    }
}

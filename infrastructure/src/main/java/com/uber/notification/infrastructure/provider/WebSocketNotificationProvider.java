package com.uber.notification.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.port.RealtimePublisherPort;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy implementation for the WEBSOCKET channel. Delivery here means "publish to Redis
 * Pub/Sub so whichever pod holds the user's live socket can push it" — it does not fail
 * just because the user is offline (that's expected; the notification is still recorded
 * in history for IN_APP/WEBSOCKET channels and picked up when they next open the app).
 */
@Component
public class WebSocketNotificationProvider implements NotificationProvider {

    private final RealtimePublisherPort realtimePublisher;
    private final ObjectMapper objectMapper;

    public WebSocketNotificationProvider(RealtimePublisherPort realtimePublisher, ObjectMapper objectMapper) {
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.WEBSOCKET;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "notificationId", notification.getId().toString(),
                    "eventType", notification.getEventType().name(),
                    "subject", notification.getRenderedSubject() == null ? "" : notification.getRenderedSubject(),
                    "body", notification.getRenderedBody() == null ? "" : notification.getRenderedBody(),
                    "createdAt", notification.getCreatedAt().toString()
            ));
            realtimePublisher.publishToUser(notification.getUserId().toString(), payload);
        } catch (Exception e) {
            throw new NotificationDeliveryException("Failed to publish websocket notification: " + e.getMessage(), true, e);
        }
    }
}

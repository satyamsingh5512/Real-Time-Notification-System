package com.uber.notification.application.provider;

import com.uber.notification.domain.model.Notification;

/**
 * Strategy interface (GoF Strategy pattern) implemented by each concrete channel adapter:
 * SES email, Twilio SMS, FCM push, WebSocket session push, and in-app DB-only persistence.
 * The dispatcher (NotificationDispatcher) selects the strategy at runtime based on
 * {@link Notification#getChannel()} without any conditional branching on channel type.
 */
public interface NotificationProvider {

    com.uber.notification.domain.model.NotificationChannel supportedChannel();

    /**
     * Attempts delivery of an already-rendered notification.
     * Implementations must throw {@code NotificationDeliveryException} with
     * {@code retryable=true} for transient failures (timeouts, 5xx, throttling)
     * and {@code retryable=false} for permanent failures (invalid address, unsubscribed).
     */
    void send(Notification notification, ProviderRecipient recipient);
}

package com.uber.notification.infrastructure.provider;

import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for the IN_APP channel. This is a no-op "provider" by design:
 * the Notification row itself (already persisted with rendered subject/body before any
 * provider's send() is invoked) IS the in-app notification — it's simply surfaced by the
 * REST history API. No external I/O is needed, so this strategy always succeeds.
 */
@Component
public class InAppNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        // Intentionally no-op: persistence already happened; see class Javadoc.
    }
}

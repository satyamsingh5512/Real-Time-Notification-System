package com.uber.notification.infrastructure.provider;

import com.google.firebase.messaging.*;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.springframework.stereotype.Component;

/** Strategy implementation for the PUSH channel using Firebase Cloud Messaging. */
@Component
public class FcmPushProvider implements NotificationProvider {

    private final FirebaseMessaging firebaseMessaging;

    public FcmPushProvider(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        if (recipient.fcmDeviceToken() == null || recipient.fcmDeviceToken().isBlank()) {
            throw new NotificationDeliveryException("Recipient has no registered device token", false);
        }
        try {
            com.google.firebase.messaging.Message message = com.google.firebase.messaging.Message.builder()
                    .setToken(recipient.fcmDeviceToken())
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(notification.getRenderedSubject() != null
                                    ? notification.getRenderedSubject() : "Notification")
                            .setBody(notification.getRenderedBody())
                            .build())
                    .build();
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            boolean retryable = switch (e.getMessagingErrorCode()) {
                case UNREGISTERED, INVALID_ARGUMENT, SENDER_ID_MISMATCH -> false;
                default -> true;
            };
            throw new NotificationDeliveryException("FCM send failed: " + e.getMessage(), retryable, e);
        } catch (Exception e) {
            throw new NotificationDeliveryException("Unexpected FCM error: " + e.getMessage(), true, e);
        }
    }
}

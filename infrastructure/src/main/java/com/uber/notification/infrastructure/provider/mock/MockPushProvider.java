package com.uber.notification.infrastructure.provider.mock;

import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local-dev stub for PUSH: logs the payload instead of calling Firebase. */
@Component
@Profile("local")
@Primary
public class MockPushProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPushProvider.class);

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        log.info("[MOCK-PUSH] token={} title={} body={} notificationId={}",
                recipient.fcmDeviceToken(), notification.getRenderedSubject(),
                notification.getRenderedBody(), notification.getId());
    }
}

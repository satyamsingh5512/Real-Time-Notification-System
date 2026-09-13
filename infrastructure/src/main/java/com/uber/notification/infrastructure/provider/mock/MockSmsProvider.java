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

/** Local-dev stub for SMS: logs the payload instead of calling Twilio. */
@Component
@Profile("local")
@Primary
public class MockSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        log.info("[MOCK-SMS] to={} body={} notificationId={}",
                recipient.phoneNumber(), notification.getRenderedBody(), notification.getId());
    }
}

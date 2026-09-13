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

/** Local-dev stub for EMAIL: logs the rendered payload instead of calling AWS SES. */
@Component
@Profile("local")
@Primary
public class MockEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockEmailProvider.class);

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        log.info("[MOCK-EMAIL] to={} subject={} body={} notificationId={}",
                recipient.email(), notification.getRenderedSubject(),
                notification.getRenderedBody(), notification.getId());
    }
}

package com.uber.notification.infrastructure.provider;

import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Strategy implementation for the SMS channel using Twilio's Programmable Messaging API. */
@Component
public class TwilioSmsProvider implements NotificationProvider {

    private final String fromNumber;

    public TwilioSmsProvider(Environment environment) {
        this.fromNumber = environment.getProperty("notification.sms.from-number", "+10000000000");
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        if (recipient.phoneNumber() == null || recipient.phoneNumber().isBlank()) {
            throw new NotificationDeliveryException("Recipient has no phone number on file", false);
        }
        try {
            Message.creator(
                    new PhoneNumber(recipient.phoneNumber()),
                    new PhoneNumber(fromNumber),
                    notification.getRenderedBody()
            ).create();
        } catch (ApiException e) {
            // Twilio error codes 401xx = auth, 213xx = invalid number -> permanent.
            boolean retryable = e.getCode() == null || e.getCode() >= 20000;
            throw new NotificationDeliveryException("Twilio send failed: " + e.getMessage(), retryable, e);
        } catch (Exception e) {
            throw new NotificationDeliveryException("Unexpected Twilio error: " + e.getMessage(), true, e);
        }
    }
}

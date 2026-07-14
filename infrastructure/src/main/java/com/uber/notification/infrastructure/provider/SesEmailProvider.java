package com.uber.notification.infrastructure.provider;

import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * Strategy implementation for the EMAIL channel using AWS SES. Registered into the
 * {@code NotificationProviderRegistry} keyed by {@code NotificationChannel.EMAIL}.
 */
@Component
public class SesEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SesEmailProvider.class);

    private final SesClient sesClient;
    private final String fromAddress;

    public SesEmailProvider(SesClient sesClient,
                             org.springframework.core.env.Environment environment) {
        this.sesClient = sesClient;
        this.fromAddress = environment.getProperty("notification.email.from-address", "no-reply@example.com");
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        if (recipient.email() == null || recipient.email().isBlank()) {
            throw new NotificationDeliveryException("Recipient has no email address on file", false);
        }
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromAddress)
                    .destination(Destination.builder().toAddresses(recipient.email()).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(
                                    notification.getRenderedSubject() != null
                                            ? notification.getRenderedSubject() : "Notification").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(notification.getRenderedBody()).build())
                                    .build())
                            .build())
                    .build();
            sesClient.sendEmail(request);
        } catch (AccountSendingPausedException | MailFromDomainNotVerifiedException e) {
            throw new NotificationDeliveryException("SES permanently rejected email: " + e.getMessage(), false, e);
        } catch (SesException e) {
            boolean retryable = e.statusCode() >= 500 || e.statusCode() == 429;
            throw new NotificationDeliveryException("SES send failed: " + e.getMessage(), retryable, e);
        } catch (Exception e) {
            throw new NotificationDeliveryException("Unexpected SES error: " + e.getMessage(), true, e);
        }
    }
}

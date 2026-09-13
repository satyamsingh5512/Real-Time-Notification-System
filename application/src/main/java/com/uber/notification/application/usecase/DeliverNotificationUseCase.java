package com.uber.notification.application.usecase;

import com.uber.notification.application.port.DeliveryMetricsPort;
import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.port.RetryPublisherPort;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.NotificationProviderRegistry;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.application.template.TemplateRenderer;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.common.util.BackoffCalculator;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationTemplate;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.NotificationTemplateRepository;

import java.time.Duration;
import java.util.Optional;

/**
 * Use case #2: renders the template for a single notification and dispatches it via the
 * Strategy-pattern {@link NotificationProvider} resolved for its channel. On transient
 * failure it schedules a retry with exponential backoff (via {@link RetryPublisherPort});
 * once attempts are exhausted, or on a permanent failure, it routes to the dead-letter topic.
 *
 * This use case is invoked both by the main event-driven flow and by the retry-topic consumer,
 * so retry handling lives in exactly one place.
 */
public class DeliverNotificationUseCase {

    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationProviderRegistry providerRegistry;
    private final RecipientResolverPort recipientResolver;
    private final RetryPublisherPort retryPublisher;
    private final DeliveryMetricsPort deliveryMetrics;

    public DeliverNotificationUseCase(NotificationRepository notificationRepository,
                                       NotificationTemplateRepository templateRepository,
                                       NotificationProviderRegistry providerRegistry,
                                       RecipientResolverPort recipientResolver,
                                       RetryPublisherPort retryPublisher) {
        this(notificationRepository, templateRepository, providerRegistry, recipientResolver,
                retryPublisher, DeliveryMetricsPort.NOOP);
    }

    public DeliverNotificationUseCase(NotificationRepository notificationRepository,
                                       NotificationTemplateRepository templateRepository,
                                       NotificationProviderRegistry providerRegistry,
                                       RecipientResolverPort recipientResolver,
                                       RetryPublisherPort retryPublisher,
                                       DeliveryMetricsPort deliveryMetrics) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.providerRegistry = providerRegistry;
        this.recipientResolver = recipientResolver;
        this.retryPublisher = retryPublisher;
        this.deliveryMetrics = deliveryMetrics == null ? DeliveryMetricsPort.NOOP : deliveryMetrics;
    }

    public void execute(Notification notification) {
        notification.markProcessing();
        notificationRepository.save(notification);

        long startNanos = System.nanoTime();
        try {
            renderTemplate(notification);
            ProviderRecipient recipient = recipientResolver.resolve(notification.getUserId());
            NotificationProvider provider = providerRegistry.resolve(notification.getChannel())
                    .orElseThrow(() -> new NotificationDeliveryException(
                            "No provider registered for channel " + notification.getChannel(), false));

            provider.send(notification, recipient);

            notification.markSent();
            notificationRepository.save(notification);
            deliveryMetrics.recordDeliveryLatency(notification.getChannel().name(),
                    Duration.ofNanos(System.nanoTime() - startNanos));
            deliveryMetrics.onDispatched(notification.getChannel().name(),
                    notification.getEventType().name(), "SUCCESS");
        } catch (NotificationDeliveryException e) {
            deliveryMetrics.recordDeliveryLatency(notification.getChannel().name(),
                    Duration.ofNanos(System.nanoTime() - startNanos));
            handleFailure(notification, e.getMessage(), e.isRetryable());
        } catch (Exception e) {
            deliveryMetrics.recordDeliveryLatency(notification.getChannel().name(),
                    Duration.ofNanos(System.nanoTime() - startNanos));
            handleFailure(notification, e.getMessage(), true);
        }
    }

    private void renderTemplate(Notification notification) {
        Optional<NotificationTemplate> template = templateRepository.findActiveByCodeAndChannel(
                notification.getTemplateCode(), notification.getChannel());

        if (template.isEmpty()) {
            notification.markRendered(null, notification.getPayload() != null
                    ? notification.getPayload().getOrDefault("message", "") : "");
            return;
        }

        NotificationTemplate t = template.get();
        String subject = TemplateRenderer.render(t.getSubjectTemplate(), notification.getPayload());
        String body = TemplateRenderer.render(t.getBodyTemplate(), notification.getPayload());
        notification.markRendered(subject, body);
    }

    private void handleFailure(Notification notification, String errorMessage, boolean retryable) {
        notification.markFailed(errorMessage, retryable);
        notificationRepository.save(notification);

        if (notification.getStatus() == com.uber.notification.domain.model.NotificationStatus.RETRYING) {
            Duration delay = BackoffCalculator.computeDelay(notification.getAttemptCount(), BASE_BACKOFF, MAX_BACKOFF);
            retryPublisher.publishForRetry(notification, delay);
            deliveryMetrics.onDispatched(notification.getChannel().name(),
                    notification.getEventType().name(), "RETRY");
        } else {
            retryPublisher.publishToDeadLetter(notification,
                    retryable ? "Max attempts exhausted" : "Permanent failure: " + errorMessage);
            deliveryMetrics.onDispatched(notification.getChannel().name(),
                    notification.getEventType().name(), "FAILURE");
        }
    }
}

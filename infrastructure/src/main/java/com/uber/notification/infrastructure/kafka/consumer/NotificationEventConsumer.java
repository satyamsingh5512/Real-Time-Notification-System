package com.uber.notification.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.event.DomainEvent;
import com.uber.notification.application.usecase.DeliverNotificationUseCase;
import com.uber.notification.application.usecase.ProcessIncomingEventUseCase;
import com.uber.notification.application.validation.EventMessageValidator;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.infrastructure.kafka.KafkaTopics;
import com.uber.notification.infrastructure.kafka.dto.EventMessage;
import com.uber.notification.infrastructure.kafka.producer.PoisonPillPublisher;
import com.uber.notification.infrastructure.metrics.NotificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes all 8 business event topics (OrderPlaced, OrderDelivered, PaymentSuccess,
 * CommentAdded, LikeReceived, Mentioned, PasswordReset, OTPGenerated). Each topic gets its
 * own {@code @KafkaListener} method (required because topic-to-consumer-group routing and
 * per-topic concurrency tuning need to be independently configurable), but all methods
 * delegate to the same {@link #handle} method so the fan-out + delivery logic is written once.
 *
 * Every event is processed asynchronously relative to the producer: this listener only
 * persists Notification rows (fast, transactional) and hands off to
 * {@link DeliverNotificationUseCase}, which is where actual provider I/O (SES/Twilio/FCM)
 * happens. A slow provider therefore never blocks Kafka consumer poll/heartbeat.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ProcessIncomingEventUseCase processIncomingEventUseCase;
    private final DeliverNotificationUseCase deliverNotificationUseCase;
    private final ObjectMapper objectMapper;
    private final PoisonPillPublisher poisonPillPublisher;
    private final NotificationMetrics notificationMetrics;

    public NotificationEventConsumer(ProcessIncomingEventUseCase processIncomingEventUseCase,
                                      DeliverNotificationUseCase deliverNotificationUseCase,
                                      ObjectMapper objectMapper,
                                      @org.springframework.beans.factory.annotation.Autowired(required = false) PoisonPillPublisher poisonPillPublisher,
                                      @org.springframework.beans.factory.annotation.Autowired(required = false) NotificationMetrics notificationMetrics) {
        this.processIncomingEventUseCase = processIncomingEventUseCase;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
        this.objectMapper = objectMapper;
        this.poisonPillPublisher = poisonPillPublisher;
        this.notificationMetrics = notificationMetrics;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_PLACED, groupId = "notification-platform-events")
    public void onOrderPlaced(String payload) {
        handle(KafkaTopics.ORDER_PLACED, payload);
    }

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-platform-events")
    public void onOrderDelivered(String payload) {
        handle(KafkaTopics.ORDER_DELIVERED, payload);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "notification-platform-events")
    public void onPaymentSuccess(String payload) {
        handle(KafkaTopics.PAYMENT_SUCCESS, payload);
    }

    @KafkaListener(topics = KafkaTopics.COMMENT_ADDED, groupId = "notification-platform-events")
    public void onCommentAdded(String payload) {
        handle(KafkaTopics.COMMENT_ADDED, payload);
    }

    @KafkaListener(topics = KafkaTopics.LIKE_RECEIVED, groupId = "notification-platform-events")
    public void onLikeReceived(String payload) {
        handle(KafkaTopics.LIKE_RECEIVED, payload);
    }

    @KafkaListener(topics = KafkaTopics.MENTIONED, groupId = "notification-platform-events")
    public void onMentioned(String payload) {
        handle(KafkaTopics.MENTIONED, payload);
    }

    @KafkaListener(topics = KafkaTopics.PASSWORD_RESET, groupId = "notification-platform-events")
    public void onPasswordReset(String payload) {
        handle(KafkaTopics.PASSWORD_RESET, payload);
    }

    @KafkaListener(topics = KafkaTopics.OTP_GENERATED, groupId = "notification-platform-events")
    public void onOtpGenerated(String payload) {
        handle(KafkaTopics.OTP_GENERATED, payload);
    }

    private void handle(String sourceTopic, String payload) {
        try {
            EventMessage message = objectMapper.readValue(payload, EventMessage.class);
            // Schema validation BEFORE fan-out: a malformed event is quarantined with a
            // precise reason instead of failing halfway through persistence (which used to
            // create partial rows and poison the retry path).
            var validation = EventMessageValidator.validate(message.eventId(), message.eventType(),
                    message.userId(), message.attributes(), message.occurredAt());
            if (!validation.valid()) {
                throw new IllegalArgumentException("schema validation failed: " + String.join("; ", validation.errors()));
            }
            if (notificationMetrics != null) {
                notificationMetrics.incrementKafkaConsumed(message.eventType().name());
            }
            DomainEvent event = new DomainEvent(
                    message.eventId(),
                    message.eventType(),
                    UUID.fromString(message.userId()),
                    message.attributes(),
                    message.occurredAt()
            );
            var notifications = processIncomingEventUseCase.execute(event);
            int deferred = 0;
            int digests = 0;
            for (Notification notification : notifications) {
                // Immediate (non-scheduled) notifications are delivered right away; the
                // scheduler job handles anything with a future scheduledFor timestamp
                // (including ATC frequency-capped deferrals).
                if (notification.getScheduledFor() == null) {
                    if (notification.getStatus() == com.uber.notification.domain.model.NotificationStatus.QUEUED_DIGEST) {
                        digests++;
                    } else {
                        deliverNotificationUseCase.execute(notification);
                    }
                } else {
                    deferred++;
                }
            }
            if (notificationMetrics != null) {
                if (digests > 0) {
                    notificationMetrics.incrementDigestQueued(message.eventType().name());
                    notificationMetrics.setPendingDigests(
                            notificationMetrics.getPendingDigests() + digests);
                }
                if (deferred > 0) {
                    notificationMetrics.incrementFrequencyCapped(message.eventType().name());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process inbound event payload: {}", payload, e);
            // Quarantine the poison pill for inspection/replay instead of silently dropping it.
            // Not rethrown: Spring Kafka's default error handler would infinitely retry it.
            if (poisonPillPublisher != null) {
                poisonPillPublisher.quarantine(sourceTopic, payload, e.getMessage());
            }
            if (notificationMetrics != null) {
                notificationMetrics.incrementPoisonPill(sourceTopic);
            }
        }
    }
}

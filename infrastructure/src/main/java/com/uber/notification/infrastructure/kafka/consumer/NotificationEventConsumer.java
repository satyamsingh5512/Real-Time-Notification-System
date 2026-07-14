package com.uber.notification.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.event.DomainEvent;
import com.uber.notification.application.usecase.DeliverNotificationUseCase;
import com.uber.notification.application.usecase.ProcessIncomingEventUseCase;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.infrastructure.kafka.KafkaTopics;
import com.uber.notification.infrastructure.kafka.dto.EventMessage;
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

    public NotificationEventConsumer(ProcessIncomingEventUseCase processIncomingEventUseCase,
                                      DeliverNotificationUseCase deliverNotificationUseCase,
                                      ObjectMapper objectMapper) {
        this.processIncomingEventUseCase = processIncomingEventUseCase;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_PLACED, groupId = "notification-platform-events")
    public void onOrderPlaced(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-platform-events")
    public void onOrderDelivered(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "notification-platform-events")
    public void onPaymentSuccess(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.COMMENT_ADDED, groupId = "notification-platform-events")
    public void onCommentAdded(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.LIKE_RECEIVED, groupId = "notification-platform-events")
    public void onLikeReceived(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.MENTIONED, groupId = "notification-platform-events")
    public void onMentioned(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.PASSWORD_RESET, groupId = "notification-platform-events")
    public void onPasswordReset(String payload) {
        handle(payload);
    }

    @KafkaListener(topics = KafkaTopics.OTP_GENERATED, groupId = "notification-platform-events")
    public void onOtpGenerated(String payload) {
        handle(payload);
    }

    private void handle(String payload) {
        try {
            EventMessage message = objectMapper.readValue(payload, EventMessage.class);
            DomainEvent event = new DomainEvent(
                    message.eventId(),
                    message.eventType(),
                    UUID.fromString(message.userId()),
                    message.attributes(),
                    message.occurredAt()
            );
            var notifications = processIncomingEventUseCase.execute(event);
            for (Notification notification : notifications) {
                // Immediate (non-scheduled) notifications are delivered right away; the
                // scheduler job handles anything with a future scheduledFor timestamp.
                if (notification.getScheduledFor() == null) {
                    deliverNotificationUseCase.execute(notification);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process inbound event payload: {}", payload, e);
            // Intentionally not rethrown: Spring Kafka's default error handler would infinitely
            // retry a poison-pill message. Malformed events are logged and dropped; consider
            // wiring a DeadLetterPublishingRecoverer here for stricter guarantees.
        }
    }
}

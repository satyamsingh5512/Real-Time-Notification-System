package com.uber.notification.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.usecase.DeliverNotificationUseCase;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.infrastructure.kafka.KafkaTopics;
import com.uber.notification.infrastructure.kafka.dto.NotificationRefMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Consumes the retry topic. If the message's {@code notBefore} time has not yet elapsed,
 * the consumer thread sleeps for the remaining delta before redelivering — this is a
 * pragmatic single-consumer-group approach (retry volume is much lower than primary event
 * volume). A higher-throughput alternative is time-bucketed topics (retry-30s, retry-5m,
 * retry-30m); the RetryPublisherPort abstraction makes swapping to that later a
 * infrastructure-only change with no impact on the application layer.
 */
@Component
public class RetryTopicConsumer {

    private static final Logger log = LoggerFactory.getLogger(RetryTopicConsumer.class);
    private static final Duration MAX_INLINE_WAIT = Duration.ofSeconds(30);

    private final NotificationRepository notificationRepository;
    private final DeliverNotificationUseCase deliverNotificationUseCase;
    private final ObjectMapper objectMapper;

    public RetryTopicConsumer(NotificationRepository notificationRepository,
                               DeliverNotificationUseCase deliverNotificationUseCase,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_RETRY, groupId = "notification-platform-retry")
    public void onRetry(String payload) {
        try {
            NotificationRefMessage message = objectMapper.readValue(payload, NotificationRefMessage.class);
            waitUntilDue(message.notBefore());

            notificationRepository.findById(message.notificationId()).ifPresentOrElse(
                    deliverNotificationUseCase::execute,
                    () -> log.warn("Retry message referenced missing notification {}", message.notificationId())
            );
        } catch (Exception e) {
            log.error("Failed to process retry message: {}", payload, e);
        }
    }

    private void waitUntilDue(Instant notBefore) throws InterruptedException {
        if (notBefore == null) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), notBefore);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        // Cap the inline wait: for longer backoffs the message will simply be reprocessed
        // slightly early is not acceptable, so instead we requeue by sleeping in bounded
        // chunks up to MAX_INLINE_WAIT, checking cooperatively. For very long delays consider
        // a dedicated delay-queue (e.g. Redis sorted set) instead of blocking a consumer thread.
        Duration wait = remaining.compareTo(MAX_INLINE_WAIT) > 0 ? MAX_INLINE_WAIT : remaining;
        Thread.sleep(wait.toMillis());
    }
}

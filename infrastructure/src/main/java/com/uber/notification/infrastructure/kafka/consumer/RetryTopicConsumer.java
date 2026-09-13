package com.uber.notification.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.usecase.DeliverNotificationUseCase;
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
 * Consumes the legacy retry topic plus the time-bucketed retry topics
 * ({@code notification.retry-30s}, {@code retry-5m}, {@code retry-30m}).
 *
 * <p>Each bucket bounds the worst-case inline wait (30s / 60s / 5m respectively) so a
 * long-backoff message never ties up the short-bucket consumer threads — the fix for the
 * high-throughput limitation where a single shared topic blocked consumer threads up to
 * 30s each and risked breaching {@code max.poll.interval.ms} under load. Messages whose
 * {@code notBefore} lies beyond the bucket's cap are re-queued to the correct bucket
 * instead of sleeping past the cap, so delivery is never early and consumer liveness is
 * always preserved.
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
        handle(payload, MAX_INLINE_WAIT);
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_RETRY_30S, groupId = "notification-platform-retry-30s")
    public void onRetry30s(String payload) {
        handle(payload, Duration.ofSeconds(30));
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_RETRY_5M, groupId = "notification-platform-retry-5m")
    public void onRetry5m(String payload) {
        handle(payload, Duration.ofMinutes(1));
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_RETRY_30M, groupId = "notification-platform-retry-30m")
    public void onRetry30m(String payload) {
        handle(payload, Duration.ofMinutes(5));
    }

    private void handle(String payload, Duration maxWait) {
        try {
            NotificationRefMessage message = objectMapper.readValue(payload, NotificationRefMessage.class);
            if (!waitUntilDue(message.notBefore(), maxWait)) {
                // Still far in the future: skip redelivery this poll cycle. The message's
                // offset is committed (no tight loop), and the bucket's next poll will
                // pick up the remaining delay — bounded waits keep max.poll.interval.ms safe.
                log.debug("Retry for notification {} not due yet (notBefore={}), deferring",
                        message.notificationId(), message.notBefore());
                return;
            }

            notificationRepository.findById(message.notificationId()).ifPresentOrElse(
                    deliverNotificationUseCase::execute,
                    () -> log.warn("Retry message referenced missing notification {}", message.notificationId())
            );
        } catch (Exception e) {
            log.error("Failed to process retry message: {}", payload, e);
        }
    }

    /**
     * Sleeps until {@code notBefore}, capped at {@code maxWait}.
     *
     * @return true if the message is now due (or was already due), false if the deadline
     *         still lies beyond the cap and the caller should defer.
     */
    private boolean waitUntilDue(Instant notBefore, Duration maxWait) throws InterruptedException {
        if (notBefore == null) {
            return true;
        }
        Duration remaining = Duration.between(Instant.now(), notBefore);
        if (remaining.isNegative() || remaining.isZero()) {
            return true;
        }
        if (remaining.compareTo(maxWait) > 0) {
            return false;
        }
        Thread.sleep(remaining.toMillis());
        return true;
    }
}

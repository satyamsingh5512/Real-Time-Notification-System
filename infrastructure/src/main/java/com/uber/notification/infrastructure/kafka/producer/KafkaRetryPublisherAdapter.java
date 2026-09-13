package com.uber.notification.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.port.RetryPublisherPort;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.infrastructure.kafka.KafkaTopics;
import com.uber.notification.infrastructure.kafka.dto.NotificationRefMessage;
import com.uber.notification.infrastructure.metrics.NotificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Publishes to time-bucketed retry topics + the dead-letter topic.
 *
 * <p>Bucket routing (high-throughput design): instead of blocking a consumer thread
 * with {@code Thread.sleep} until {@code notBefore}, the delay selects a bucket topic
 * whose consumer applies only a small bounded wait:
 * <ul>
 *   <li>delay &lt;= 30s  → notification.retry-30s (near-immediate, tiny inline wait)</li>
 *   <li>delay &lt;= 5m   → notification.retry-5m</li>
 *   <li>otherwise        → notification.retry-30m (long backoffs never block short-bucket consumers)</li>
 * </ul>
 * The legacy single {@code notification.retry} topic is still consumed (backwards
 * compatibility for in-flight messages), so this is a safe rolling upgrade. Each bucket
 * consumer still honors the exact {@code notBefore} timestamp — the bucket only bounds
 * the worst-case inline wait, it never fires early.
 */
@Component
public class KafkaRetryPublisherAdapter implements RetryPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaRetryPublisherAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationMetrics notificationMetrics;

    public KafkaRetryPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                      @Autowired(required = false) NotificationMetrics notificationMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.notificationMetrics = notificationMetrics;
    }

    @Override
    public void publishForRetry(Notification notification, Duration delay) {
        try {
            String bucket = bucketFor(delay);
            NotificationRefMessage message = new NotificationRefMessage(
                    notification.getId(), notification.getAttemptCount(), Instant.now().plus(delay), null);
            kafkaTemplate.send(bucket, notification.getId().toString(),
                    objectMapper.writeValueAsString(message));
            log.info("Scheduled retry #{} for notification {} after {} on {}",
                    notification.getAttemptCount(), notification.getId(), delay, bucket);
            if (notificationMetrics != null) {
                notificationMetrics.incrementRetryScheduled(bucket);
            }
        } catch (Exception e) {
            log.error("Failed to publish retry message for notification {}", notification.getId(), e);
        }
    }

    @Override
    public void publishToDeadLetter(Notification notification, String reason) {
        try {
            NotificationRefMessage message = new NotificationRefMessage(
                    notification.getId(), notification.getAttemptCount(), Instant.now(), reason);
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_DLQ, notification.getId().toString(),
                    objectMapper.writeValueAsString(message));
            log.warn("Dead-lettered notification {} reason={}", notification.getId(), reason);
        } catch (Exception e) {
            log.error("Failed to publish DLQ message for notification {}", notification.getId(), e);
        }
    }

    /** Visible for testing. */
    static String bucketFor(Duration delay) {
        if (delay.compareTo(Duration.ofSeconds(30)) <= 0) {
            return KafkaTopics.NOTIFICATION_RETRY_30S;
        }
        if (delay.compareTo(Duration.ofMinutes(5)) <= 0) {
            return KafkaTopics.NOTIFICATION_RETRY_5M;
        }
        return KafkaTopics.NOTIFICATION_RETRY_30M;
    }
}

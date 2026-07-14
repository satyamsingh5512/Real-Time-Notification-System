package com.uber.notification.infrastructure.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.application.port.RetryPublisherPort;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.infrastructure.kafka.KafkaTopics;
import com.uber.notification.infrastructure.kafka.dto.NotificationRefMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Publishes to the retry / dead-letter topics. Rather than using Kafka's native delayed-message
 * mechanism (which doesn't exist natively), this implementation stamps a {@code notBefore}
 * timestamp on the message and relies on {@code RetryTopicConsumer} to pause/re-poll until
 * that time has elapsed. For high-volume production use this could be swapped for
 * time-bucketed retry topics (retry-30s, retry-5m, retry-30m) without changing the port contract.
 */
@Component
public class KafkaRetryPublisherAdapter implements RetryPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaRetryPublisherAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRetryPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishForRetry(Notification notification, Duration delay) {
        try {
            NotificationRefMessage message = new NotificationRefMessage(
                    notification.getId(), notification.getAttemptCount(), Instant.now().plus(delay), null);
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_RETRY, notification.getId().toString(),
                    objectMapper.writeValueAsString(message));
            log.info("Scheduled retry #{} for notification {} after {}",
                    notification.getAttemptCount(), notification.getId(), delay);
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
}

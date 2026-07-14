package com.uber.notification.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.infrastructure.kafka.KafkaTopics;
import com.uber.notification.infrastructure.kafka.dto.NotificationRefMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the dead-letter topic purely for observability/audit persistence: the notification
 * row itself is already marked DEAD_LETTERED by {@code DeliverNotificationUseCase} before this
 * message is even published, so this listener's job is alerting/metrics hooks (e.g. emit a
 * metric, notify an on-call channel) rather than further state mutation.
 */
@Component
public class DeadLetterTopicConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterTopicConsumer.class);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public DeadLetterTopicConsumer(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_DLQ, groupId = "notification-platform-dlq")
    public void onDeadLetter(String payload) {
        try {
            NotificationRefMessage message = objectMapper.readValue(payload, NotificationRefMessage.class);
            log.error("DEAD_LETTER notification={} attempts={} reason={}",
                    message.notificationId(), message.attemptNumber(), message.reason());
            // TODO integrate with alerting (PagerDuty/Slack webhook) and a metrics counter here.
        } catch (Exception e) {
            log.error("Failed to process DLQ message: {}", payload, e);
        }
    }
}

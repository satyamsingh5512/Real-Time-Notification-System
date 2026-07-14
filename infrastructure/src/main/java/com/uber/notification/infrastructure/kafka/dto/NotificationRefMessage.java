package com.uber.notification.infrastructure.kafka.dto;

import java.time.Instant;
import java.util.UUID;

/** Wire format for the retry/delivery/DLQ internal topics: carries just the notification ID. */
public record NotificationRefMessage(
        UUID notificationId,
        int attemptNumber,
        Instant notBefore,
        String reason
) {
}

package com.uber.notification.api.dto.notification;

import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String eventType,
        NotificationChannel channel,
        String subject,
        String body,
        NotificationStatus status,
        String priority,
        boolean read,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getEventType().name(), n.getChannel(),
                n.getRenderedSubject(), n.getRenderedBody(), n.getStatus(),
                n.getPriority() == null ? "MEDIUM" : n.getPriority().name(),
                n.isRead(), n.getCreatedAt(), n.getSentAt()
        );
    }
}

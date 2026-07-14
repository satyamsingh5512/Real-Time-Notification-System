package com.uber.notification.infrastructure.persistence.mapper;

import com.uber.notification.domain.model.Notification;
import com.uber.notification.infrastructure.persistence.entity.NotificationJpaEntity;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationJpaEntity toEntity(Notification n) {
        return new NotificationJpaEntity(
                n.getId(), n.getUserId(), n.getEventType(), n.getChannel(), n.getTemplateCode(),
                n.getPayload(), n.getRenderedSubject(), n.getRenderedBody(), n.getStatus(),
                n.getAttemptCount(), n.getMaxAttempts(), n.getLastErrorMessage(), n.getScheduledFor(),
                n.getSentAt(), n.getReadAt(), n.isDeleted(), n.getCreatedAt(), n.getUpdatedAt(),
                n.getIdempotencyKey()
        );
    }

    public static Notification toDomain(NotificationJpaEntity e) {
        return Notification.restore(
                e.getId(), e.getUserId(), e.getEventType(), e.getChannel(), e.getTemplateCode(),
                e.getPayload(), e.getRenderedSubject(), e.getRenderedBody(), e.getStatus(),
                e.getAttemptCount(), e.getMaxAttempts(), e.getLastErrorMessage(), e.getScheduledFor(),
                e.getSentAt(), e.getReadAt(), e.isDeleted(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getIdempotencyKey()
        );
    }
}

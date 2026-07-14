package com.uber.notification.infrastructure.persistence.mapper;

import com.uber.notification.domain.model.NotificationTemplate;
import com.uber.notification.infrastructure.persistence.entity.NotificationTemplateJpaEntity;

public final class NotificationTemplateMapper {

    private NotificationTemplateMapper() {
    }

    public static NotificationTemplateJpaEntity toEntity(NotificationTemplate t) {
        return new NotificationTemplateJpaEntity(
                t.getId(), t.getCode(), t.getChannel(), t.getVersion(), t.getSubjectTemplate(),
                t.getBodyTemplate(), t.getLocale(), t.isActive(), t.getCreatedAt()
        );
    }

    public static NotificationTemplate toDomain(NotificationTemplateJpaEntity e) {
        return new NotificationTemplate(
                e.getId(), e.getCode(), e.getChannel(), e.getVersion(), e.getSubjectTemplate(),
                e.getBodyTemplate(), e.getLocale(), e.isActive(), e.getCreatedAt()
        );
    }
}

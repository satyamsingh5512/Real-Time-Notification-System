package com.uber.notification.api.dto.template;

import com.uber.notification.domain.model.NotificationTemplate;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id, String code, String channel, int version,
        String subjectTemplate, String bodyTemplate, String locale, boolean active, Instant createdAt
) {
    public static TemplateResponse from(NotificationTemplate t) {
        return new TemplateResponse(t.getId(), t.getCode(), t.getChannel().name(), t.getVersion(),
                t.getSubjectTemplate(), t.getBodyTemplate(), t.getLocale(), t.isActive(), t.getCreatedAt());
    }
}

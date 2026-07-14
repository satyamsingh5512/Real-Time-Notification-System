package com.uber.notification.api.dto.template;

import com.uber.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.NotBlank;

public record CreateTemplateRequest(
        @NotBlank String code,
        NotificationChannel channel,
        String locale,
        String subjectTemplate,
        @NotBlank String bodyTemplate
) {
}

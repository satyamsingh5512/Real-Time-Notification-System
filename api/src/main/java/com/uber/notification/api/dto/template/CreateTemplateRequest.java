package com.uber.notification.api.dto.template;

import com.uber.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateTemplateRequest(
        @NotBlank String code,
        NotificationChannel channel,
        String locale,
        String subjectTemplate,
        @NotBlank String bodyTemplate,
        /** Gradual rollout percentage (0-100). Omitted = 100 (immediate cutover). */
        @Min(0) @Max(100) Integer trafficPct
) {
}
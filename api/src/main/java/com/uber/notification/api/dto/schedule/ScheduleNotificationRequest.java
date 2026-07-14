package com.uber.notification.api.dto.schedule;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ScheduleNotificationRequest(
        @NotNull UUID userId,
        @NotNull EventType eventType,
        @NotNull NotificationChannel channel,
        @NotBlank String templateCode,
        Map<String, String> payload,
        @NotNull @Future Instant scheduledFor
) {
}

package com.uber.notification.api.dto.preference;

import com.uber.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.NotNull;

public record ChannelOptInRequest(
        @NotNull NotificationChannel channel,
        @NotNull Boolean enabled
) {
}

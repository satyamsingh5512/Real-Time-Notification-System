package com.uber.notification.api.dto.preference;

import jakarta.validation.constraints.NotNull;

/** Sets the notification intent for an event type (ALL / MENTIONS / MUTE). */
public record IntentRequest(@NotNull com.uber.notification.domain.model.NotificationIntent intent) {
}
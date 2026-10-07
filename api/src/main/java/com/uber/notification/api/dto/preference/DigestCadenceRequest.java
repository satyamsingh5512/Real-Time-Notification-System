package com.uber.notification.api.dto.preference;

import jakarta.validation.constraints.NotNull;

/** Sets the digest batching cadence for an event type (OFF / DAILY / WEEKLY). */
public record DigestCadenceRequest(@NotNull com.uber.notification.domain.model.DigestCadence cadence) {
}
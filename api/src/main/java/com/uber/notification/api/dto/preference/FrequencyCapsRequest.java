package com.uber.notification.api.dto.preference;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Payload for the ATC-style push frequency caps (daily budget + minimum gap). */
public record FrequencyCapsRequest(
        @Min(0) @Max(50) int maxPushesPerDay,
        @Min(0) @Max(168) int minHoursBetweenPushes
) {
}
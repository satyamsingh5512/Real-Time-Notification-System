package com.uber.notification.api.dto.preference;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuietHoursRequest(
        @NotNull Boolean enabled,
        @Min(0) @Max(23) int startHour,
        @Min(0) @Max(23) int endHour
) {
}

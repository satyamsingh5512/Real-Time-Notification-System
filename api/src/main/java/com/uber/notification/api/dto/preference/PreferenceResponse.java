package com.uber.notification.api.dto.preference;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.UserPreference;

import java.util.Map;

public record PreferenceResponse(
        String eventType,
        Map<NotificationChannel, Boolean> channelOptIn,
        boolean quietHoursEnabled,
        int quietHoursStart,
        int quietHoursEnd
) {
    public static PreferenceResponse from(UserPreference p) {
        return new PreferenceResponse(
                p.getEventType().name(), p.getChannelOptIn(),
                p.isQuietHoursEnabled(), p.getQuietHoursStart(), p.getQuietHoursEnd()
        );
    }
}

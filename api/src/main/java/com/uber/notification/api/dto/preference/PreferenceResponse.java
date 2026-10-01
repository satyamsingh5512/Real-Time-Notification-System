package com.uber.notification.api.dto.preference;

import com.uber.notification.domain.model.DigestCadence;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationIntent;
import com.uber.notification.domain.model.UserPreference;

import java.util.Map;

/**
 * Intent (whether to notify) is deliberately separate from delivery (how):
 * the Slack model that replaced four overlapping preference paradigms.
 */
public record PreferenceResponse(
        String eventType,
        Map<NotificationChannel, Boolean> channelOptIn,
        boolean quietHoursEnabled,
        int quietHoursStart,
        int quietHoursEnd,
        String intent,
        boolean pushEnabled,
        String digestCadence,
        int maxPushesPerDay,
        int minHoursBetweenPushes
) {
    public static PreferenceResponse from(UserPreference p) {
        NotificationIntent intent = p.getIntent() == null ? NotificationIntent.ALL : p.getIntent();
        DigestCadence cadence = p.getDigestCadence() == null ? DigestCadence.OFF : p.getDigestCadence();
        return new PreferenceResponse(
                p.getEventType().name(), p.getChannelOptIn(),
                p.isQuietHoursEnabled(), p.getQuietHoursStart(), p.getQuietHoursEnd(),
                intent.name(), p.isPushEnabled(), cadence.name(),
                p.getMaxPushesPerDay(), p.getMinHoursBetweenPushes()
        );
    }
}
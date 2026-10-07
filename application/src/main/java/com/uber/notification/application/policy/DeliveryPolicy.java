package com.uber.notification.application.policy;

import com.uber.notification.domain.model.DigestCadence;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationPriority;
import com.uber.notification.domain.model.UserPreference;

/**
 * Decides whether a notification should be batched into the user's pending digest
 * instead of being sent immediately (Slack/SuprSend pattern).
 *
 * <ul>
 *   <li>Only EMAIL is digestible (in-app/WebSocket still arrive instantly).</li>
 *   <li>Only LOW/MEDIUM priority — HIGH/critical always sends immediately.</li>
 *   <li>Only when the user opted into a digest cadence (not OFF).</li>
 *   <li>Security-critical events (OTP, password reset) never digest.</li>
 * </ul>
 */
public final class DeliveryPolicy {

    private DeliveryPolicy() {
    }

    public static boolean shouldDigest(NotificationChannel channel,
                                       NotificationPriority priority,
                                       EventType eventType,
                                       UserPreference preference) {
        if (channel != NotificationChannel.EMAIL) {
            return false;
        }
        if (priority == NotificationPriority.HIGH) {
            return false;
        }
        if (eventType == EventType.OTP_GENERATED || eventType == EventType.PASSWORD_RESET) {
            return false;
        }
        if (preference == null) {
            return false;
        }
        return preference.getDigestCadence() != DigestCadence.OFF;
    }
}

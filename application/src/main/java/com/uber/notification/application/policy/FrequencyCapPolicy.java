package com.uber.notification.application.policy;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.NotificationRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * LinkedIn Air-Traffic-Controller style per-user frequency caps for push.
 * Enforced at fan-out time: over-budget pushes are deferred (SCHEDULED at the
 * earliest allowed instant) rather than dropped, so nothing is silently lost.
 */
public class FrequencyCapPolicy {

    private final NotificationRepository notificationRepository;

    public FrequencyCapPolicy(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public enum Verdict { ALLOW, DEFER }

    public record Decision(Verdict verdict, Instant deliverAt) {
        public static Decision allow() {
            return new Decision(Verdict.ALLOW, null);
        }

        public static Decision deferUntil(Instant at) {
            return new Decision(Verdict.DEFER, at);
        }
    }

    public Decision check(UUID userId, NotificationChannel channel, UserPreference preference, Instant now) {
        if (channel != NotificationChannel.PUSH) {
            return Decision.allow();
        }
        int maxPerDay = preference != null ? preference.getMaxPushesPerDay() : 2;
        int minGapHours = preference != null ? preference.getMinHoursBetweenPushes() : 8;

        if (maxPerDay > 0) {
            long sentLast24h = notificationRepository.countPushesSentSince(userId, now.minus(Duration.ofHours(24)));
            if (sentLast24h >= maxPerDay) {
                // Next budget frees when the oldest send in the window ages out; approximate
                // with now + min gap so the scheduler re-checks rather than busy-looping.
                return Decision.deferUntil(now.plus(Duration.ofHours(Math.max(1, minGapHours))));
            }
        }
        if (minGapHours > 0) {
            var lastSent = notificationRepository.findLastPushSentAt(userId);
            if (lastSent.isPresent()) {
                Instant earliest = lastSent.get().plus(Duration.ofHours(minGapHours));
                if (now.isBefore(earliest)) {
                    return Decision.deferUntil(earliest);
                }
            }
        }
        return Decision.allow();
    }
}

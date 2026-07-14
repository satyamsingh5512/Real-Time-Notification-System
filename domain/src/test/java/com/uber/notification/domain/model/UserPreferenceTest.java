package com.uber.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferenceTest {

    private UserPreference newPreference() {
        return new UserPreference(UUID.randomUUID(), UUID.randomUUID(), EventType.COMMENT_ADDED,
                new EnumMap<>(NotificationChannel.class), false, 0, 0, Instant.now());
    }

    @Test
    void defaultsToEnabledWhenNoExplicitPreference() {
        assertThat(newPreference().isChannelEnabled(NotificationChannel.PUSH)).isTrue();
    }

    @Test
    void respectsExplicitOptOut() {
        UserPreference p = newPreference();
        p.setChannelEnabled(NotificationChannel.PUSH, false);
        assertThat(p.isChannelEnabled(NotificationChannel.PUSH)).isFalse();
    }

    @Test
    void quietHoursWithinSameDayRange() {
        UserPreference p = newPreference();
        p.setQuietHours(true, 9, 17);
        assertThat(p.isWithinQuietHours(12)).isTrue();
        assertThat(p.isWithinQuietHours(8)).isFalse();
        assertThat(p.isWithinQuietHours(17)).isFalse();
    }

    @Test
    void quietHoursWrappingMidnight() {
        UserPreference p = newPreference();
        p.setQuietHours(true, 22, 6);
        assertThat(p.isWithinQuietHours(23)).isTrue();
        assertThat(p.isWithinQuietHours(2)).isTrue();
        assertThat(p.isWithinQuietHours(12)).isFalse();
    }

    @Test
    void quietHoursDisabledAlwaysReturnsFalse() {
        UserPreference p = newPreference();
        p.setQuietHours(false, 9, 17);
        assertThat(p.isWithinQuietHours(12)).isFalse();
    }
}

package com.uber.notification.domain.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-user, per-event-type opt-in/opt-out matrix across channels. This is the domain
 * source of truth consulted by the fan-out use case before dispatching to any provider.
 */
public class UserPreference {

    private final UUID id;
    private final UUID userId;
    private final EventType eventType;
    private final Map<NotificationChannel, Boolean> channelOptIn;
    private boolean quietHoursEnabled;
    private int quietHoursStart; // 0-23 local hour
    private int quietHoursEnd;   // 0-23 local hour
    private Instant updatedAt;

    public UserPreference(UUID id, UUID userId, EventType eventType,
                           Map<NotificationChannel, Boolean> channelOptIn,
                           boolean quietHoursEnabled, int quietHoursStart, int quietHoursEnd,
                           Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.eventType = Objects.requireNonNull(eventType);
        this.channelOptIn = channelOptIn != null ? channelOptIn : new EnumMap<>(NotificationChannel.class);
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.updatedAt = updatedAt;
    }

    /** Defaults to opted-in when no explicit preference row exists for a channel. */
    public boolean isChannelEnabled(NotificationChannel channel) {
        return channelOptIn.getOrDefault(channel, Boolean.TRUE);
    }

    public void setChannelEnabled(NotificationChannel channel, boolean enabled) {
        channelOptIn.put(channel, enabled);
        this.updatedAt = Instant.now();
    }

    public boolean isWithinQuietHours(int currentLocalHour) {
        if (!quietHoursEnabled) {
            return false;
        }
        if (quietHoursStart == quietHoursEnd) {
            return false;
        }
        if (quietHoursStart < quietHoursEnd) {
            return currentLocalHour >= quietHoursStart && currentLocalHour < quietHoursEnd;
        }
        // wraps midnight, e.g. 22 -> 6
        return currentLocalHour >= quietHoursStart || currentLocalHour < quietHoursEnd;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Map<NotificationChannel, Boolean> getChannelOptIn() {
        return channelOptIn;
    }

    public boolean isQuietHoursEnabled() {
        return quietHoursEnabled;
    }

    public int getQuietHoursStart() {
        return quietHoursStart;
    }

    public int getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHours(boolean enabled, int startHour, int endHour) {
        this.quietHoursEnabled = enabled;
        this.quietHoursStart = startHour;
        this.quietHoursEnd = endHour;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

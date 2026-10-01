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
    // Intent vs delivery split (Slack model): intent decides *whether* to notify,
    // channelOptIn + pushEnabled decide *how*.
    private NotificationIntent intent = NotificationIntent.ALL;
    private boolean pushEnabled = true;
    private DigestCadence digestCadence = DigestCadence.OFF;
    // Air-Traffic-Controller style frequency caps for push.
    private int maxPushesPerDay = 2;
    private int minHoursBetweenPushes = 8;

    public UserPreference(UUID id, UUID userId, EventType eventType,
                           Map<NotificationChannel, Boolean> channelOptIn,
                           boolean quietHoursEnabled, int quietHoursStart, int quietHoursEnd,
                           Instant updatedAt) {
        this(id, userId, eventType, channelOptIn, quietHoursEnabled, quietHoursStart,
                quietHoursEnd, updatedAt, NotificationIntent.ALL, true, DigestCadence.OFF, 2, 8);
    }

    public UserPreference(UUID id, UUID userId, EventType eventType,
                           Map<NotificationChannel, Boolean> channelOptIn,
                           boolean quietHoursEnabled, int quietHoursStart, int quietHoursEnd,
                           Instant updatedAt, NotificationIntent intent, boolean pushEnabled,
                           DigestCadence digestCadence, int maxPushesPerDay, int minHoursBetweenPushes) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.eventType = Objects.requireNonNull(eventType);
        this.channelOptIn = channelOptIn != null ? channelOptIn : new EnumMap<>(NotificationChannel.class);
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.updatedAt = updatedAt;
        this.intent = intent != null ? intent : NotificationIntent.ALL;
        this.pushEnabled = pushEnabled;
        this.digestCadence = digestCadence != null ? digestCadence : DigestCadence.OFF;
        this.maxPushesPerDay = Math.max(0, maxPushesPerDay);
        this.minHoursBetweenPushes = Math.max(0, minHoursBetweenPushes);
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

    public NotificationIntent getIntent() {
        return intent;
    }

    public void setIntent(NotificationIntent intent) {
        this.intent = intent != null ? intent : NotificationIntent.ALL;
        this.updatedAt = Instant.now();
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
        this.updatedAt = Instant.now();
    }

    public DigestCadence getDigestCadence() {
        return digestCadence;
    }

    public void setDigestCadence(DigestCadence digestCadence) {
        this.digestCadence = digestCadence != null ? digestCadence : DigestCadence.OFF;
        this.updatedAt = Instant.now();
    }

    public int getMaxPushesPerDay() {
        return maxPushesPerDay;
    }

    public int getMinHoursBetweenPushes() {
        return minHoursBetweenPushes;
    }

    public void setFrequencyCaps(int maxPushesPerDay, int minHoursBetweenPushes) {
        this.maxPushesPerDay = Math.max(0, maxPushesPerDay);
        this.minHoursBetweenPushes = Math.max(0, minHoursBetweenPushes);
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

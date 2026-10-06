package com.uber.notification.infrastructure.persistence.entity;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_preference_event", columnNames = {"user_id", "event_type"})
})
public class UserPreferenceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Type(JsonType.class)
    @Column(name = "channel_opt_in", columnDefinition = "jsonb")
    private Map<NotificationChannel, Boolean> channelOptIn;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start", nullable = false)
    private int quietHoursStart;

    @Column(name = "quiet_hours_end", nullable = false)
    private int quietHoursEnd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.uber.notification.domain.model.NotificationIntent intent =
            com.uber.notification.domain.model.NotificationIntent.ALL;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "digest_cadence", nullable = false)
    private com.uber.notification.domain.model.DigestCadence digestCadence =
            com.uber.notification.domain.model.DigestCadence.OFF;

    @Column(name = "max_pushes_per_day", nullable = false)
    private int maxPushesPerDay = 2;

    @Column(name = "min_hours_between_pushes", nullable = false)
    private int minHoursBetweenPushes = 8;

    protected UserPreferenceJpaEntity() {
    }

    public UserPreferenceJpaEntity(UUID id, UUID userId, EventType eventType,
                                    Map<NotificationChannel, Boolean> channelOptIn,
                                    boolean quietHoursEnabled, int quietHoursStart, int quietHoursEnd,
                                    Instant updatedAt) {
        this(id, userId, eventType, channelOptIn, quietHoursEnabled, quietHoursStart,
                quietHoursEnd, updatedAt, com.uber.notification.domain.model.NotificationIntent.ALL,
                true, com.uber.notification.domain.model.DigestCadence.OFF, 2, 8);
    }

    public UserPreferenceJpaEntity(UUID id, UUID userId, EventType eventType,
                                    Map<NotificationChannel, Boolean> channelOptIn,
                                    boolean quietHoursEnabled, int quietHoursStart, int quietHoursEnd,
                                    Instant updatedAt,
                                    com.uber.notification.domain.model.NotificationIntent intent,
                                    boolean pushEnabled,
                                    com.uber.notification.domain.model.DigestCadence digestCadence,
                                    int maxPushesPerDay, int minHoursBetweenPushes) {
        this.id = id;
        this.userId = userId;
        this.eventType = eventType;
        this.channelOptIn = channelOptIn;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.updatedAt = updatedAt;
        this.intent = intent != null ? intent : com.uber.notification.domain.model.NotificationIntent.ALL;
        this.pushEnabled = pushEnabled;
        this.digestCadence = digestCadence != null ? digestCadence
                : com.uber.notification.domain.model.DigestCadence.OFF;
        this.maxPushesPerDay = maxPushesPerDay;
        this.minHoursBetweenPushes = minHoursBetweenPushes;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public EventType getEventType() { return eventType; }
    public Map<NotificationChannel, Boolean> getChannelOptIn() { return channelOptIn; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public int getQuietHoursStart() { return quietHoursStart; }
    public int getQuietHoursEnd() { return quietHoursEnd; }
    public Instant getUpdatedAt() { return updatedAt; }
    public com.uber.notification.domain.model.NotificationIntent getIntent() { return intent; }
    public boolean isPushEnabled() { return pushEnabled; }
    public com.uber.notification.domain.model.DigestCadence getDigestCadence() { return digestCadence; }
    public int getMaxPushesPerDay() { return maxPushesPerDay; }
    public int getMinHoursBetweenPushes() { return minHoursBetweenPushes; }
}

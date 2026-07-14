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

    protected UserPreferenceJpaEntity() {
    }

    public UserPreferenceJpaEntity(UUID id, UUID userId, EventType eventType,
                                    Map<NotificationChannel, Boolean> channelOptIn,
                                    boolean quietHoursEnabled, int quietHoursStart, int quietHoursEnd,
                                    Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.eventType = eventType;
        this.channelOptIn = channelOptIn;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public EventType getEventType() { return eventType; }
    public Map<NotificationChannel, Boolean> getChannelOptIn() { return channelOptIn; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public int getQuietHoursStart() { return quietHoursStart; }
    public int getQuietHoursEnd() { return quietHoursEnd; }
    public Instant getUpdatedAt() { return updatedAt; }
}

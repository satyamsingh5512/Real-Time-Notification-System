package com.uber.notification.infrastructure.persistence.entity;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_status", columnList = "status"),
        @Index(name = "idx_notifications_idempotency_key", columnList = "idempotency_key", unique = true)
})
public class NotificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> payload;

    @Column(name = "rendered_subject")
    private String renderedSubject;

    @Column(name = "rendered_body", columnDefinition = "text")
    private String renderedBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Version
    private long version;

    protected NotificationJpaEntity() {
    }

    public NotificationJpaEntity(UUID id, UUID userId, EventType eventType, NotificationChannel channel,
                                  String templateCode, Map<String, String> payload, String renderedSubject,
                                  String renderedBody, NotificationStatus status, int attemptCount,
                                  int maxAttempts, String lastErrorMessage, Instant scheduledFor,
                                  Instant sentAt, Instant readAt, boolean deleted, Instant createdAt,
                                  Instant updatedAt, String idempotencyKey) {
        this.id = id;
        this.userId = userId;
        this.eventType = eventType;
        this.channel = channel;
        this.templateCode = templateCode;
        this.payload = payload;
        this.renderedSubject = renderedSubject;
        this.renderedBody = renderedBody;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.lastErrorMessage = lastErrorMessage;
        this.scheduledFor = scheduledFor;
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public EventType getEventType() { return eventType; }
    public NotificationChannel getChannel() { return channel; }
    public String getTemplateCode() { return templateCode; }
    public Map<String, String> getPayload() { return payload; }
    public String getRenderedSubject() { return renderedSubject; }
    public String getRenderedBody() { return renderedBody; }
    public NotificationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getScheduledFor() { return scheduledFor; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isDeleted() { return deleted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}

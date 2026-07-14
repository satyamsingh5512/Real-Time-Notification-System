package com.uber.notification.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The core aggregate: a single notification instance targeted at one user on one channel,
 * derived from an inbound event. This is the unit of delivery, retry, and history tracking.
 */
public class Notification {

    private final UUID id;
    private final UUID userId;
    private final EventType eventType;
    private final NotificationChannel channel;
    private final String templateCode;
    private final Map<String, String> payload; // rendering context captured from the event
    private String renderedSubject;
    private String renderedBody;
    private NotificationStatus status;
    private int attemptCount;
    private final int maxAttempts;
    private String lastErrorMessage;
    private Instant scheduledFor;      // null => deliver immediately
    private Instant sentAt;
    private Instant readAt;
    private boolean deleted;
    private final Instant createdAt;
    private Instant updatedAt;
    private final String idempotencyKey;

    public Notification(UUID id, UUID userId, EventType eventType, NotificationChannel channel,
                         String templateCode, Map<String, String> payload, int maxAttempts,
                         Instant scheduledFor, Instant createdAt, String idempotencyKey) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.eventType = Objects.requireNonNull(eventType);
        this.channel = Objects.requireNonNull(channel);
        this.templateCode = Objects.requireNonNull(templateCode);
        this.payload = payload;
        this.maxAttempts = maxAttempts;
        this.scheduledFor = scheduledFor;
        this.status = scheduledFor != null ? NotificationStatus.SCHEDULED : NotificationStatus.PENDING;
        this.attemptCount = 0;
        this.deleted = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.idempotencyKey = idempotencyKey;
    }

    /** Reconstruction constructor used by persistence mappers. */
    public static Notification restore(UUID id, UUID userId, EventType eventType, NotificationChannel channel,
                                        String templateCode, Map<String, String> payload,
                                        String renderedSubject, String renderedBody, NotificationStatus status,
                                        int attemptCount, int maxAttempts, String lastErrorMessage,
                                        Instant scheduledFor, Instant sentAt, Instant readAt, boolean deleted,
                                        Instant createdAt, Instant updatedAt, String idempotencyKey) {
        Notification n = new Notification(id, userId, eventType, channel, templateCode, payload,
                maxAttempts, scheduledFor, createdAt, idempotencyKey);
        n.renderedSubject = renderedSubject;
        n.renderedBody = renderedBody;
        n.status = status;
        n.attemptCount = attemptCount;
        n.lastErrorMessage = lastErrorMessage;
        n.sentAt = sentAt;
        n.readAt = readAt;
        n.deleted = deleted;
        n.updatedAt = updatedAt;
        return n;
    }

    public boolean isDueForDelivery(Instant now) {
        return status == NotificationStatus.PENDING
                || (status == NotificationStatus.SCHEDULED && !now.isBefore(scheduledFor))
                || status == NotificationStatus.RETRYING;
    }

    public boolean canRetry() {
        return attemptCount < maxAttempts;
    }

    public void markRendered(String subject, String body) {
        this.renderedSubject = subject;
        this.renderedBody = body;
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = NotificationStatus.PROCESSING;
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.updatedAt = sentAt;
    }

    public void markFailed(String errorMessage) {
        markFailed(errorMessage, true);
    }

    /**
     * Records a failed delivery attempt. When {@code retryable} is false (a permanent
     * failure such as an invalid address) the notification is dead-lettered immediately
     * regardless of remaining attempts; otherwise it transitions to RETRYING as long as
     * attempts remain, or DEAD_LETTERED once exhausted.
     */
    public void markFailed(String errorMessage, boolean retryable) {
        this.lastErrorMessage = errorMessage;
        this.status = (retryable && canRetry()) ? NotificationStatus.RETRYING : NotificationStatus.DEAD_LETTERED;
        this.updatedAt = Instant.now();
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void markUnread() {
        this.readAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void softDelete() {
        this.deleted = true;
        this.updatedAt = Instant.now();
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

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public String getRenderedSubject() {
        return renderedSubject;
    }

    public String getRenderedBody() {
        return renderedBody;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

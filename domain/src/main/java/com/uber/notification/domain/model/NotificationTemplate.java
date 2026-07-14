package com.uber.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A versioned, channel-specific message template. Rendering substitutes {{placeholders}}
 * with values from the event payload. Versioning allows safe rollout/rollback of copy changes
 * without redeploying application code.
 */
public class NotificationTemplate {

    private final UUID id;
    private final String code;              // e.g. "ORDER_PLACED"
    private final NotificationChannel channel;
    private final int version;
    private String subjectTemplate;         // null for SMS/push/websocket
    private String bodyTemplate;
    private String locale;                  // e.g. "en-US"
    private boolean active;
    private final Instant createdAt;

    public NotificationTemplate(UUID id, String code, NotificationChannel channel, int version,
                                 String subjectTemplate, String bodyTemplate, String locale,
                                 boolean active, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.channel = Objects.requireNonNull(channel);
        this.version = version;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = Objects.requireNonNull(bodyTemplate);
        this.locale = locale != null ? locale : "en-US";
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public int getVersion() {
        return version;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public String getLocale() {
        return locale;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void deactivate() {
        this.active = false;
    }
}

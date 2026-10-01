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
    /** Gradual rollout gate (DoorDash pattern): % of users rendering this version (0-100). */
    private int trafficPct = 100;

    public NotificationTemplate(UUID id, String code, NotificationChannel channel, int version,
                                 String subjectTemplate, String bodyTemplate, String locale,
                                 boolean active, Instant createdAt) {
        this(id, code, channel, version, subjectTemplate, bodyTemplate, locale, active, createdAt, 100);
    }

    public NotificationTemplate(UUID id, String code, NotificationChannel channel, int version,
                                 String subjectTemplate, String bodyTemplate, String locale,
                                 boolean active, Instant createdAt, int trafficPct) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.channel = Objects.requireNonNull(channel);
        this.version = version;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = Objects.requireNonNull(bodyTemplate);
        this.locale = locale != null ? locale : "en-US";
        this.active = active;
        this.createdAt = createdAt;
        this.trafficPct = Math.min(100, Math.max(0, trafficPct));
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

    public int getTrafficPct() {
        return trafficPct;
    }

    public void setTrafficPct(int trafficPct) {
        this.trafficPct = Math.min(100, Math.max(0, trafficPct));
    }

    /**
     * Stable rollout bucket for a user: same user always resolves to the same
     * template version for a given code (no flip-flopping between renders).
     */
    public boolean servesBucket(int bucket) {
        return bucket < trafficPct;
    }

    public static int bucketFor(java.util.UUID userId, String code) {
        return Math.abs((userId.toString() + "|" + code).hashCode()) % 100;
    }
}

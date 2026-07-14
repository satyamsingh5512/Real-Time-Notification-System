package com.uber.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_templates", indexes = {
        @Index(name = "idx_templates_code_channel", columnList = "code, channel")
})
public class NotificationTemplateJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.uber.notification.domain.model.NotificationChannel channel;

    @Column(nullable = false)
    private int version;

    @Column(name = "subject_template")
    private String subjectTemplate;

    @Column(name = "body_template", columnDefinition = "text", nullable = false)
    private String bodyTemplate;

    @Column(nullable = false)
    private String locale;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationTemplateJpaEntity() {
    }

    public NotificationTemplateJpaEntity(UUID id, String code, com.uber.notification.domain.model.NotificationChannel channel,
                                          int version, String subjectTemplate, String bodyTemplate, String locale,
                                          boolean active, Instant createdAt) {
        this.id = id;
        this.code = code;
        this.channel = channel;
        this.version = version;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
        this.locale = locale;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public com.uber.notification.domain.model.NotificationChannel getChannel() { return channel; }
    public int getVersion() { return version; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public String getBodyTemplate() { return bodyTemplate; }
    public String getLocale() { return locale; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}

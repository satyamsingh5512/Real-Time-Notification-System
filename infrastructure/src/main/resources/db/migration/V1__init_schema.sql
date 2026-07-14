-- V1__init_schema.sql
-- Core schema for the Notification Platform. All tables use UUID primary keys generated
-- application-side (IdGenerator) rather than DB sequences, so IDs are known before insert
-- (needed for idempotency-key based dedup and cross-service correlation).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- users & RBAC
-- ============================================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    phone_number    VARCHAR(32),
    fcm_device_token VARCHAR(512),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================================
-- notification_templates: versioned, channel + locale specific copy
-- ============================================================================
CREATE TABLE notification_templates (
    id               UUID PRIMARY KEY,
    code             VARCHAR(128) NOT NULL,
    channel          VARCHAR(32) NOT NULL,
    version          INT NOT NULL,
    subject_template VARCHAR(512),
    body_template    TEXT NOT NULL,
    locale           VARCHAR(16) NOT NULL DEFAULT 'en-US',
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_templates_code_channel ON notification_templates(code, channel);
-- Only one active version per (code, channel, locale) at a time.
CREATE UNIQUE INDEX uq_templates_active_version
    ON notification_templates(code, channel, locale)
    WHERE active = TRUE;

-- ============================================================================
-- user_preferences: per-user, per-event-type channel opt-in matrix + quiet hours
-- ============================================================================
CREATE TABLE user_preferences (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type          VARCHAR(64) NOT NULL,
    channel_opt_in      JSONB NOT NULL DEFAULT '{}',
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start   INT NOT NULL DEFAULT 0,
    quiet_hours_end     INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_preference_event UNIQUE (user_id, event_type)
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);

-- ============================================================================
-- notifications: the core delivery/history aggregate
-- ============================================================================
CREATE TABLE notifications (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type         VARCHAR(64) NOT NULL,
    channel            VARCHAR(32) NOT NULL,
    template_code      VARCHAR(128) NOT NULL,
    payload            JSONB,
    rendered_subject   VARCHAR(512),
    rendered_body      TEXT,
    status             VARCHAR(32) NOT NULL,
    attempt_count      INT NOT NULL DEFAULT 0,
    max_attempts       INT NOT NULL DEFAULT 5,
    last_error_message TEXT,
    scheduled_for      TIMESTAMPTZ,
    sent_at            TIMESTAMPTZ,
    read_at            TIMESTAMPTZ,
    deleted            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key    VARCHAR(255) NOT NULL UNIQUE,
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_idempotency_key ON notifications(idempotency_key);
-- Speeds up the scheduler's due-for-delivery poll (status + scheduled_for range scan).
CREATE INDEX idx_notifications_due_poll ON notifications(status, scheduled_for);
-- Speeds up paginated history queries ordered by recency, excluding soft-deleted rows.
CREATE INDEX idx_notifications_user_history ON notifications(user_id, deleted, created_at DESC);
-- Speeds up unread-count queries.
CREATE INDEX idx_notifications_unread ON notifications(user_id, read_at) WHERE deleted = FALSE;

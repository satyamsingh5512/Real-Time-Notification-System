-- V4__digest_frequency_rollout.sql
-- Professional notification-platform optimizations (Slack digest / LinkedIn ATC / DoorDash flags):
--   1. Digest batching: QUEUED_DIGEST rows flushed as one email per user per window.
--      (QUEUED_DIGEST is a status value; statuses are stored as VARCHAR, no CHECK to alter.)
--   2. Per-user frequency caps (Air-Traffic-Controller style): daily push budget +
--      minimum gap between pushes, enforced at fan-out time.
--   3. Intent vs delivery split: `intent` (should-notify) separate from channel
--      delivery toggles, plus digest cadence preference.
--   4. Template gradual rollout: traffic_pct gates new template versions.

ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS intent VARCHAR(16) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS digest_cadence VARCHAR(16) NOT NULL DEFAULT 'OFF',
    ADD COLUMN IF NOT EXISTS max_pushes_per_day INT NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS min_hours_between_pushes INT NOT NULL DEFAULT 8;

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS traffic_pct INT NOT NULL DEFAULT 100;

-- Digest flush scan: pending digests per user, oldest first.
CREATE INDEX IF NOT EXISTS idx_notifications_digest_flush
    ON notifications(status, user_id, created_at)
    WHERE deleted = FALSE;

-- Frequency-cap window scan: recent push sends per user.
CREATE INDEX IF NOT EXISTS idx_notifications_user_push_window
    ON notifications(user_id, channel, sent_at)
    WHERE sent_at IS NOT NULL;

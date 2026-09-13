-- V3__notification_priority.sql
-- Adds the LOW/MEDIUM/HIGH delivery priority to notifications (production spec:
-- "Priority levels: LOW, MEDIUM, HIGH"). Backfills existing rows to MEDIUM.

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM';

-- Rewrite the default so future inserts must come through the application default explicitly.
ALTER TABLE notifications ALTER COLUMN priority DROP DEFAULT;

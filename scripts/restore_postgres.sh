#!/usr/bin/env bash
# Restore a backup created by backup_postgres.sh into the prod compose stack.
# Usage: scripts/restore_postgres.sh /opt/rtns/backups/notification_platform_<ts>.sql.gz
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DB_NAME="${DB_NAME:-notification_platform}"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <backup.sql.gz>" >&2
  exit 1
fi
BACKUP="$1"
[[ -f "$BACKUP" ]] || { echo "backup not found: $BACKUP" >&2; exit 1; }

echo "[restore] verifying gzip integrity..."
gzip -t "$BACKUP"

echo "[restore] stopping app (keep postgres running)..."
docker compose -f "$COMPOSE_FILE" stop notification-platform || true

echo "[restore] restoring into '$DB_NAME'..."
gunzip -c "$BACKUP" | docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -U "${DB_USER:?set DB_USER}" -d "$DB_NAME" -v ON_ERROR_STOP=1

echo "[restore] verification: notifications table row count:"
docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT COUNT(*) FROM notifications;"

echo "[restore] restart app:"
docker compose -f "$COMPOSE_FILE" up -d notification-platform
echo "[restore] OK — run scripts/smoke_test.sh to confirm."

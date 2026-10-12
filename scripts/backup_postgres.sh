#!/usr/bin/env bash
# Daily PostgreSQL backup for the prod compose stack.
# Writes compressed, timestamped dumps to BACKUP_DIR with retention cleanup.
# Cron example: 0 2 * * * /opt/rtns/scripts/backup_postgres.sh >> /var/log/rtns-backup.log 2>&1
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BACKUP_DIR="${BACKUP_DIR:-/opt/rtns/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
DB_NAME="${DB_NAME:-notification_platform}"

mkdir -p "$BACKUP_DIR"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$BACKUP_DIR/${DB_NAME}_${TS}.sql.gz"

echo "[backup] dumping database '$DB_NAME' -> $OUT"
docker compose -f "$COMPOSE_FILE" exec -T postgres \
  pg_dump -U "${DB_USER:?set DB_USER}" -d "$DB_NAME" | gzip > "$OUT"

echo "[backup] verifying gzip integrity..."
gzip -t "$OUT"

echo "[backup] pruning dumps older than ${RETENTION_DAYS}d..."
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

ls -lh "$OUT"
echo "[backup] OK"

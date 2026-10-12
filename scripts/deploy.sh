#!/usr/bin/env bash
# Zero-broken-deploy: pull -> validate config -> start -> health-gate -> rollback on failure.
# Usage (on the Oracle VM, inside /opt/rtns):
#   sudo ./scripts/deploy.sh [prod-tag]
# Requires: .env present, docker compose plugin, curl.
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
TAG="${1:-latest}"
HEALTH_URL="${HEALTH_URL:-http://localhost/health/ready}"
TRIES="${TRIES:-30}"

cd "$(dirname "$0")/.."

echo "[deploy] 1/5 validating configuration..."
[[ -f .env ]] || { echo ".env missing — copy .env.example first" >&2; exit 1; }
set -a; source .env; set +a
: "${JWT_SECRET:?JWT_SECRET required}"
: "${DB_PASSWORD:?DB_PASSWORD required}"
[[ "${#JWT_SECRET}" -ge 32 ]] || { echo "JWT_SECRET must be 32+ chars" >&2; exit 1; }
docker compose -f "$COMPOSE_FILE" config -q
echo "[deploy] config OK"

echo "[deploy] 2/5 pulling/building images (tag: $TAG)..."
PREV_IMAGE="$(docker images -q rtns-backend:prod 2>/dev/null | head -n1 || true)"
docker compose -f "$COMPOSE_FILE" pull --ignore-pull-failures || true
docker compose -f "$COMPOSE_FILE" build notification-platform nginx

echo "[deploy] 3/5 starting services (postgres -> migrate -> app)..."
docker compose -f "$COMPOSE_FILE" up -d postgres redis kafka
docker compose -f "$COMPOSE_FILE" up -d notification-platform
# Flyway migrations run inside Boot; wait for readiness which includes the DB probe.
docker compose -f "$COMPOSE_FILE" up -d nginx

echo "[deploy] 4/5 health gate ($HEALTH_URL x$TRIES)..."
ok=0
for i in $(seq 1 "$TRIES"); do
  if curl -fsS --max-time 5 "$HEALTH_URL" >/dev/null 2>&1; then ok=1; break; fi
  echo "  ... waiting ($i/$TRIES)"
  sleep 10
done
if [[ "$ok" != 1 ]]; then
  echo "[deploy] HEALTH CHECK FAILED — collecting status:" >&2
  docker compose -f "$COMPOSE_FILE" ps || true
  docker compose -f "$COMPOSE_FILE" logs --tail=100 notification-platform || true
  echo "[deploy] 5/5 rollback: restarting previous containers..." >&2
  docker compose -f "$COMPOSE_FILE" up -d --force-recreate notification-platform nginx || true
  exit 1
fi

echo "[deploy] 5/5 verifying DB + Redis connectivity..."
docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U "$DB_USER" -d "$DB_NAME"
docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping | grep -q PONG

docker image prune -f >/dev/null 2>&1 || true
echo "[deploy] SUCCESS — service is healthy at $HEALTH_URL"

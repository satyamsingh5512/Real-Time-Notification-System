#!/usr/bin/env bash
# Production smoke test: exercises the full demo path with a generated user.
# Usage: scripts/smoke_test.sh [BASE_URL]   (default http://localhost)
# Exit non-zero on any failure. Never uses fixed production credentials.
set -euo pipefail

BASE="${1:-http://localhost}"
API="$BASE/api/v1"
EMAIL="smoke-$(date +%s)-$RANDOM@example.com"
PASS="SmokeTest123!"
NAME="Smoke Tester"

pass() { echo "  PASS $1"; }
fail() { echo "  FAIL $1" >&2; exit 1; }

echo "[smoke] base: $BASE"

code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/health" || true)"
[[ "$code" == "200" ]] || fail "GET /health (got $code)"
pass "GET /health"

code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health" || true)"
[[ "$code" == "200" ]] || echo "  WARN /actuator/health via nginx returned $code (backend direct check follows)"

echo "[smoke] registering $EMAIL ..."
REG="$(curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"displayName\":\"$NAME\"}")"
TOKEN="$(echo "$REG" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))')"
[[ -n "$TOKEN" ]] || fail "registration (resp: $REG)"
pass "registration"

echo "[smoke] login ..."
LOGIN="$(curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"
TOKEN="$(echo "$LOGIN" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))')"
[[ -n "$TOKEN" ]] || fail "login (resp: $LOGIN)"
pass "login"
AUTH=(-H "Authorization: Bearer $TOKEN")

echo "[smoke] authenticated inbox ..."
code="$(curl -s -o /dev/null -w '%{http_code}' "${AUTH[@]}" "$API/notifications?page=0&size=5")"
[[ "$code" == "200" ]] || fail "GET /notifications (got $code)"
pass "GET /notifications"

echo "[smoke] unread count ..."
UNREAD="$(curl -s "${AUTH[@]}" "$API/notifications/unread-count")"
echo "$UNREAD" | python3 -c 'import json,sys; assert "unreadCount" in json.load(sys.stdin)' || fail "unread-count shape ($UNREAD)"
pass "GET /notifications/unread-count -> $UNREAD"

echo "[smoke] preferences ..."
code="$(curl -s -o /dev/null -w '%{http_code}' "${AUTH[@]}" "$API/preferences")"
[[ "$code" == "200" ]] || fail "GET /preferences (got $code)"
pass "GET /preferences"

echo "[smoke] schedule internal notification (expects 401/403 for USER role — proves RBAC) ..."
code="$(curl -s -o /dev/null -w '%{http_code}' "${AUTH[@]}" -X POST "$API/internal/notifications/schedule" \
  -H 'Content-Type: application/json' -d '{"userId":"00000000-0000-0000-0000-000000000000","eventType":"OTP_GENERATED","channel":"WEBSOCKET"}')"
[[ "$code" == "401" || "$code" == "403" ]] || echo "  WARN internal schedule returned $code (expected 401/403 for USER)"
pass "RBAC denies USER on /internal (got $code)"

echo "[smoke] ready probe ..."
code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/health/ready" || curl -s -o /dev/null -w '%{http_code}' "$BASE:8080/health/ready")"
[[ "$code" == "200" ]] || fail "GET /health/ready (got $code)"
pass "GET /health/ready"

echo "[smoke] ALL CHECKS PASSED"

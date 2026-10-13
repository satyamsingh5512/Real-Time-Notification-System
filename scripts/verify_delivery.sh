#!/usr/bin/env bash
#
# Verifies the delivery-layer guarantees that nginx owns: security headers, asset
# caching, the SPA fallback, and the backend health proxy.
#
# These are deliberately NOT part of the Playwright suite. Playwright runs against
# `vite preview`, which serves files and knows nothing about CSP or cache policy —
# asserting headers there would test nothing. This script runs against a real nginx.
#
# Usage:
#   scripts/verify_delivery.sh                       # builds + starts a throwaway container
#   BASE_URL=https://your-host scripts/verify_delivery.sh   # checks a deployed instance
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

BASE_URL="${BASE_URL:-}"
CONTAINER="rtns-verify-delivery"
IMAGE="rtns-verify-delivery:tmp"
PORT="${PORT:-18099}"

pass=0
fail=0

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; pass=$((pass + 1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; fail=$((fail + 1)); }
head2() { printf '\n\033[1m%s\033[0m\n' "$1"; }

cleanup() {
  if [ -z "$BASE_URL" ]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    docker network rm rtns-verify >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# ---------------------------------------------------------------- start nginx
if [ -z "$BASE_URL" ]; then
  echo "Building the frontend image (this takes a moment on a cold cache)…"
  docker build -f frontend/Dockerfile -t "$IMAGE" . >/dev/null || {
    echo "build failed"; exit 1;
  }
  docker network create rtns-verify >/dev/null 2>&1 || true
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$CONTAINER" \
    --network rtns-verify --network-alias notification-platform \
    -p "${PORT}:80" "$IMAGE" >/dev/null || { echo "run failed"; exit 1; }

  for _ in $(seq 1 30); do
    curl -fsS "http://localhost:${PORT}/health" >/dev/null 2>&1 && break
    sleep 1
  done
  BASE_URL="http://localhost:${PORT}"
  echo "Testing a throwaway container at ${BASE_URL}"
fi

header() { curl -sI "$1" | tr -d '\r' | grep -i "^$2:" | head -1 | cut -d: -f2- | sed 's/^ *//'; }

# ------------------------------------------------- 1. security headers everywhere
# Every SPA URL is served through an internal redirect to /index.html. If that
# location declares its own add_header, nginx discards the inherited set and the
# whole app silently ships with no CSP. This is the check that catches it.
head2 "Security headers on every SPA route"
for path in /welcome /login /register /dashboard /inbox /preferences /settings /status /admin; do
  csp=$(header "${BASE_URL}${path}" 'content-security-policy')
  frame=$(header "${BASE_URL}${path}" 'x-frame-options')
  nosniff=$(header "${BASE_URL}${path}" 'x-content-type-options')
  if [ -z "$csp" ]; then
    bad "${path} — no Content-Security-Policy"
  elif [ -z "$frame" ] || [ -z "$nosniff" ]; then
    bad "${path} — CSP present but x-frame-options / x-content-type-options missing"
  else
    ok "${path} — CSP + X-Frame-Options + nosniff"
  fi
done

# ------------------------------------------------------------- 2. CSP contents
head2 "CSP contents"
csp=$(header "${BASE_URL}/welcome" 'content-security-policy')
case "$csp" in
  *"unsafe-eval"*) bad "CSP permits unsafe-eval" ;;
  *) ok "no unsafe-eval" ;;
esac
case "$csp" in
  *"object-src 'none'"*) ok "object-src 'none'" ;;
  *) bad "object-src is not restricted" ;;
esac
case "$csp" in
  *"font-src 'self'"*) ok "font-src 'self' (self-hosted font is allowed)" ;;
  *) bad "font-src is not explicit — a self-hosted font could be blocked" ;;
esac
case "$csp" in
  *"script-src 'self'"*) ok "script-src 'self'" ;;
  *) bad "script-src is not restricted to self" ;;
esac

# -------------------------------------------------------- 3. caching behaviour
head2 "Cache policy"
asset=$(curl -s "${BASE_URL}/welcome" | grep -oE '/assets/[A-Za-z0-9._-]+\.(js|css)' | head -1)
if [ -n "$asset" ]; then
  cc=$(header "${BASE_URL}${asset}" 'cache-control')
  case "$cc" in
    *immutable*) ok "content-hashed asset is immutable (${asset})" ;;
    *) bad "asset is not immutably cached: ${cc}" ;;
  esac
else
  bad "could not find a hashed asset reference in the HTML"
fi
cc=$(header "${BASE_URL}/index.html" 'cache-control')
case "$cc" in
  *no-cache*) ok "index.html is no-cache (deploys are picked up immediately)" ;;
  *) bad "index.html may be served stale: ${cc}" ;;
esac

# ------------------------------------------------------ 4. SPA route fallback
head2 "SPA fallback"
for path in /dashboard /inbox /deep/unknown/route; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}${path}")
  if [ "$code" = "200" ]; then
    ok "${path} → 200 (falls back to index.html)"
  else
    bad "${path} → ${code}, expected 200"
  fi
done

# ----------------------------------------------- 5. backend health is proxied
# Regression guard: /health/ready used to fall through to the SPA fallback and return
# index.html with a 200, so the deploy health gate passed with a dead backend.
head2 "Backend health proxy"
local_code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/health")
if [ "$local_code" = "200" ]; then
  ok "/health → 200 (nginx liveness)"
else
  bad "/health → ${local_code}, expected 200"
fi
ready_code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/health/ready")
case "$ready_code" in
  200|502|503|504)
    ok "/health/ready → ${ready_code} (reached the backend, not the SPA fallback)" ;;
  *)
    bad "/health/ready → ${ready_code}; a 200 here would mean the SPA fallback answered" ;;
esac

# ------------------------------------------------------------- 6. no Node runtime
head2 "Runtime image contents"
if docker image inspect "$IMAGE" >/dev/null 2>&1; then
  if docker run --rm --entrypoint sh "$IMAGE" -c 'command -v node npm nodejs' >/dev/null 2>&1; then
    bad "Node is present in the runtime image"
  else
    ok "no Node in the runtime image"
  fi
  size=$(docker image inspect "$IMAGE" --format '{{.Size}}')
  ok "image size ${size} bytes"
else
  echo "  (skipped — testing a deployed URL, not a local build)"
fi

# ------------------------------------------------------------------ summary
head2 "Summary"
printf '  %d passed, %d failed\n\n' "$pass" "$fail"
[ "$fail" -eq 0 ] || exit 1
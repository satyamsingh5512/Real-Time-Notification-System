#!/usr/bin/env bash
# Reproducible latency/throughput probe for a small single-instance deployment.
# Measures REAL numbers — paste results into docs/PERFORMANCE.md, never invent them.
# Usage: scripts/benchmark.sh [BASE_URL] [REQUESTS] [CONCURRENCY]
# Requires: backend running + a demo user. Creates its own throwaway user.
set -euo pipefail

BASE="${1:-http://localhost:8080}"
N="${2:-200}"
C="${3:-10}"
API="$BASE/api/v1"
EMAIL="bench-$(date +%s)-$RANDOM@example.com"
PASS="BenchTest123!"

echo "[bench] registering throwaway user..."
TOKEN="$(curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"displayName\":\"bench\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"

echo "[bench] $N GET /api/v1/notifications (concurrency $C) as $EMAIL"
START=$(date +%s%N)
echo "$TOKEN" > /tmp/rtns-bench-token
seq 1 "$N" | xargs -P "$C" -I{} curl -s -o /dev/null -w '%{http_code} %{time_total}\n' \
  -H "Authorization: Bearer $TOKEN" "$API/notifications?page=0&size=20" > /tmp/rtns-bench.out
END=$(date +%s%N)

python3 - "$N" <<'EOF'
import sys, statistics
n = int(sys.argv[1])
rows = [l.split() for l in open('/tmp/rtns-bench.out') if l.strip()]
codes = [r[0] for r in rows]
lat = sorted(float(r[1]) * 1000 for r in rows)
ok = sum(1 for c in codes if c == '200')
print(f"requests:      {len(rows)} (asked {n})")
print(f"success (200): {ok}")
print(f"p50 latency:   {statistics.median(lat):.1f} ms" if lat else "p50: n/a")
print(f"p95 latency:   {lat[int(len(lat)*0.95)-1]:.1f} ms" if lat else "p95: n/a")
print(f"max latency:   {lat[-1]:.1f} ms" if lat else "max: n/a")
EOF
ELAPSED_MS=$(( (END - START) / 1000000 ))
echo "wall time:     ${ELAPSED_MS} ms"
python3 -c "print(f'throughput:     {int($N / ($ELAPSED_MS/1000))} req/s')"
echo "[bench] raw samples: /tmp/rtns-bench.out (commit the summary, not invented numbers)"

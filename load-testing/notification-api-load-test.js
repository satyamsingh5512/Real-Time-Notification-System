// k6 load test for the Notification Platform REST API.
//
// Simulates a realistic mixed workload: users logging in, checking their notification
// inbox/unread count, marking notifications read, and updating preferences — the read-heavy
// endpoints that must stay fast under load since they're on the hot path of every app open.
//
// Run:  k6 run load-testing/notification-api-load-test.js
// Or with a custom target: k6 run -e BASE_URL=https://staging.example.com load-testing/notification-api-load-test.js
import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

const loginFailureRate = new Rate("login_failure_rate");
const notificationFetchDuration = new Trend("notification_fetch_duration_ms");

export const options = {
  scenarios: {
    ramping_load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 50 },   // ramp up
        { duration: "2m", target: 200 },   // sustained load
        { duration: "1m", target: 500 },   // peak burst (e.g. flash sale notification fan-out)
        { duration: "30s", target: 0 },    // ramp down
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<500", "p(99)<1000"],
    http_req_failed: ["rate<0.01"],
    login_failure_rate: ["rate<0.01"],
  },
};

function randomEmail(vuId, iterId) {
  return `loadtest-user-${vuId}-${iterId}@example.com`;
}

export function setup() {
  // Pre-register a pool of users so the login-heavy scenario doesn't hammer the
  // registration/BCrypt-hashing path (which is intentionally slow) during the actual test.
  const users = [];
  for (let i = 0; i < 20; i++) {
    const email = `loadtest-pool-${i}@example.com`;
    const password = "LoadTest123!";
    http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
      email, password, displayName: `Load Test User ${i}`,
    }), { headers: { "Content-Type": "application/json" } });
    users.push({ email, password });
  }
  return { users };
}

export default function (data) {
  const user = data.users[Math.floor(Math.random() * data.users.length)];

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: user.email,
    password: user.password,
  }), { headers: { "Content-Type": "application/json" } });

  const loginOk = check(loginRes, { "login succeeded": (r) => r.status === 200 });
  loginFailureRate.add(!loginOk);
  if (!loginOk) {
    sleep(1);
    return;
  }

  const token = loginRes.json("token");
  const authHeaders = { headers: { Authorization: `Bearer ${token}` } };

  const historyStart = Date.now();
  const historyRes = http.get(`${BASE_URL}/api/v1/notifications?page=0&size=20`, authHeaders);
  notificationFetchDuration.add(Date.now() - historyStart);
  check(historyRes, { "history fetch succeeded": (r) => r.status === 200 });

  const unreadRes = http.get(`${BASE_URL}/api/v1/notifications/unread-count`, authHeaders);
  check(unreadRes, { "unread count fetch succeeded": (r) => r.status === 200 });

  const notifications = historyRes.json();
  if (Array.isArray(notifications) && notifications.length > 0) {
    const target = notifications[0];
    const markReadRes = http.patch(`${BASE_URL}/api/v1/notifications/${target.id}/read`, null, authHeaders);
    check(markReadRes, { "mark read succeeded": (r) => r.status === 200 });
  }

  const preferencesRes = http.get(`${BASE_URL}/api/v1/preferences`, authHeaders);
  check(preferencesRes, { "preferences fetch succeeded": (r) => r.status === 200 });

  sleep(Math.random() * 2 + 1); // simulate think time between 1-3s
}

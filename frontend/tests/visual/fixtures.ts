import type { Page, Route } from '@playwright/test';

/**
 * API fixtures for visual regression.
 *
 * These exist only in the test harness. They let the suite capture *populated* states
 * of authenticated pages deterministically, without standing up Postgres, Redis and
 * Kafka, and without the 8×viewport matrix becoming flaky on live data.
 *
 * This is not application mocking: `src/services/api.ts` is untouched and still talks
 * to the real backend. Playwright intercepts at the network layer, so the app runs its
 * genuine fetch → parse → render path against a controlled response.
 */

const TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2Ny04OWFiLTAxZGQtYjg5YS1hYmNkZWYxMjM0NTY3Iiwicm9sZXMiOiJVU0VSLCJleHAiOjk5OTk5OTk5OTl9.signature';

const USER = {
  token: TOKEN,
  userId: '01234567-89ab-cdef-0123-456789abcdef',
  email: 'avery.chen@example.com',
  displayName: 'Avery Chen',
  roles: ['USER', 'ADMIN'],
};

const ADMIN = { ...USER, roles: ['ADMIN'] };

function iso(minutesAgo: number): string {
  return new Date(Date.now() - minutesAgo * 60_000).toISOString();
}

function notification(
  id: string,
  eventType: string,
  channel: string,
  subject: string,
  body: string,
  minutesAgo: number,
  read: boolean,
  priority: 'LOW' | 'MEDIUM' | 'HIGH' = 'MEDIUM',
  status = 'DELIVERED',
) {
  return {
    id,
    eventType,
    channel,
    subject,
    body,
    status,
    priority,
    read,
    createdAt: iso(minutesAgo),
    sentAt: iso(minutesAgo - 1),
  };
}

/** A representative inbox: mixed channels, statuses, priorities and read states. */
export const NOTIFICATIONS = [
  notification(
    'n-01',
    'PAYMENT_SUCCESS',
    'EMAIL',
    'Payment of $248.00 confirmed',
    'Your card ending 4242 was charged for order #88120. A receipt is on its way.',
    4,
    false,
    'HIGH',
  ),
  notification(
    'n-02',
    'MENTIONED',
    'PUSH',
    'Priya Raman mentioned you',
    '"Can you take a look at the retry policy before we ship?" in #incident-2291',
    38,
    false,
  ),
  notification(
    'n-03',
    'ORDER_PLACED',
    'IN_APP',
    'Order #88120 placed',
    'Two items · arrives Thursday. You will get a notification when it ships.',
    96,
    false,
  ),
  notification(
    'n-04',
    'ORDER_DELIVERED',
    'SMS',
    'Order #88001 delivered',
    'Left with a neighbour at 14 Martin Street.',
    160,
    true,
  ),
  notification(
    'n-05',
    'COMMENT_ADDED',
    'WEBSOCKET',
    'New comment on "Digest batching"',
    '"Should the flush job respect quiet hours?" — this seems worth a decision.',
    320,
    true,
    'LOW',
  ),
  notification(
    'n-06',
    'LIKE_RECEIVED',
    'IN_APP',
    'Marcus Webb liked your comment',
    'On "Frequency caps defer rather than drop".',
    1_500,
    true,
    'LOW',
  ),
  notification(
    'n-07',
    'ORDER_DELIVERED',
    'EMAIL',
    'Order #87940 delivered',
    'Signed for by the recipient.',
    4_100,
    true,
  ),
  notification(
    'n-08',
    'PASSWORD_RESET',
    'EMAIL',
    'Your password was reset',
    'If this was not you, contact your administrator immediately.',
    9_000,
    true,
    'HIGH',
  ),
];

export const UNREAD_COUNT = { unreadCount: 3 };

export const ADMIN_STATS = {
  totalNotifications: 184_392,
  totalUsers: 1_204,
  notificationsToday: 2_481,
  unreadNotifications: 11_927,
  readRatePercentage: 71.4,
};

export const PREFERENCES = [
  {
    eventType: 'ORDER_PLACED',
    channelOptIn: { EMAIL: true, PUSH: true, IN_APP: true },
    quietHoursEnabled: true,
    quietHoursStart: 22,
    quietHoursEnd: 7,
    intent: 'ALL',
    pushEnabled: true,
    digestCadence: 'DAILY',
    maxPushesPerDay: 4,
    minHoursBetweenPushes: 8,
  },
  {
    eventType: 'MENTIONED',
    channelOptIn: { PUSH: true, IN_APP: true, WEBSOCKET: true },
    quietHoursEnabled: false,
    quietHoursStart: 0,
    quietHoursEnd: 0,
    intent: 'MENTIONS',
    pushEnabled: true,
    digestCadence: 'OFF',
    maxPushesPerDay: 0,
    minHoursBetweenPushes: 0,
  },
  {
    eventType: 'COMMENT_ADDED',
    channelOptIn: { PUSH: true, IN_APP: true, WEBSOCKET: true },
    quietHoursEnabled: false,
    quietHoursStart: 0,
    quietHoursEnd: 0,
    intent: 'ALL',
    pushEnabled: true,
    digestCadence: 'OFF',
    maxPushesPerDay: 10,
    minHoursBetweenPushes: 2,
  },
  {
    eventType: 'LIKE_RECEIVED',
    channelOptIn: { IN_APP: true, WEBSOCKET: true },
    quietHoursEnabled: true,
    quietHoursStart: 23,
    quietHoursEnd: 6,
    intent: 'MUTE',
    pushEnabled: false,
    digestCadence: 'WEEKLY',
    maxPushesPerDay: 1,
    minHoursBetweenPushes: 24,
  },
];

export const TEMPLATE_HISTORY = [
  {
    id: 't-01',
    code: 'ORDER_PLACED',
    channel: 'EMAIL',
    version: 4,
    subjectTemplate: 'Order {{orderId}} confirmed',
    bodyTemplate:
      'Thanks! Your order {{orderId}} for {{amount}} is being prepared. Track it at {{trackingUrl}}.',
    locale: 'en-US',
    active: true,
    createdAt: iso(60 * 24 * 9),
    trafficPct: 100,
  },
  {
    id: 't-02',
    code: 'ORDER_PLACED',
    channel: 'EMAIL',
    version: 3,
    subjectTemplate: 'We received your order {{orderId}}',
    bodyTemplate: 'Thanks for your order {{orderId}}.',
    locale: 'en-US',
    active: false,
    createdAt: iso(60 * 24 * 41),
    trafficPct: 100,
  },
  {
    id: 't-03',
    code: 'ORDER_PLACED',
    channel: 'SMS',
    version: 1,
    subjectTemplate: null,
    bodyTemplate: 'Order {{orderId}} placed. Track: {{trackingUrl}}',
    locale: 'en-US',
    active: true,
    createdAt: iso(60 * 24 * 120),
    trafficPct: 25,
  },
];

export const DELIVERY_METRICS = {
  PENDING: 14,
  SCHEDULED: 226,
  PROCESSING: 38,
  SENT: 1_904,
  DELIVERED: 171_338,
  FAILED: 892,
  RETRYING: 61,
  DEAD_LETTERED: 47,
  CANCELLED: 3,
  QUEUED_DIGEST: 118,
};

const HEALTH = {
  base: { status: 'UP', service: 'notification-platform' },
  live: { status: 'UP', liveness: 'CORRECT' },
  ready: { status: 'UP', readiness: 'ACCEPTING_TRAFFIC', database: 'UP' },
};

type Mode = 'populated' | 'empty' | 'error';

export interface SeedOptions {
  /** `empty` returns empty collections so the empty states can be captured. */
  mode?: Mode;
  /** `error` makes every API call fail, to capture the error/recovery UI. */
  user?: typeof USER | typeof ADMIN;
}

/**
 * Registers network routes and seeds the stored session, so authenticated pages render.
 * Registered before `page.goto` so the very first fetch is already answered.
 */
export async function seedApp(page: Page, options: SeedOptions = {}): Promise<void> {
  const { mode = 'populated', user = USER } = options;

  await page.addInitScript(
    ([token, storedUser]) => {
      window.localStorage.setItem('rtns.token', token as string);
      window.localStorage.setItem('rtns.user', JSON.stringify(storedUser));
    },
    [user.token, user] as [string, unknown],
  );

  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      headers: { 'access-control-allow-origin': '*' },
      body: JSON.stringify(body),
    });

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;

    if (mode === 'error') {
      // 503 exercises the "service unavailable" copy rather than a crash.
      return json(route, { status: 503, errorCode: 'SERVICE_UNAVAILABLE', message: 'unavailable' }, 503);
    }

    if (path === '/api/v1/notifications/unread-count') {
      return json(route, mode === 'empty' ? { unreadCount: 0 } : UNREAD_COUNT);
    }
    if (path === '/api/v1/notifications') {
      return json(route, mode === 'empty' ? [] : NOTIFICATIONS);
    }
    if (path === '/api/v1/preferences') {
      return json(route, mode === 'empty' ? [] : PREFERENCES);
    }
    if (path === '/api/v1/admin/stats') {
      return json(route, ADMIN_STATS);
    }
    if (path === '/api/v1/admin/delivery-metrics') {
      return json(route, DELIVERY_METRICS);
    }
    if (path === '/api/v1/admin/active-sessions') {
      return json(route, { activeWebSocketSessions: 37 });
    }
    if (path.startsWith('/api/v1/admin/templates/')) {
      return json(route, TEMPLATE_HISTORY);
    }
    // Mass-email endpoints. The panel reads the quota on mount, so without this the
    // daily-budget card would render as "Unknown" in every screenshot.
    if (path === '/api/v1/admin/email/quota') {
      return json(route, { used: 12, limit: 100, reachable: true, remaining: 88 });
    }
    if (path === '/api/v1/admin/email/campaigns/preview') {
      return json(route, {
        campaignId: 'preview',
        dryRun: true,
        scanned: 1204,
        targeted: 1173,
        skippedOptedOut: 31,
        sent: 0,
        failed: 0,
        truncated: false,
        quotaLimitPerDay: 100,
        quotaUsedToday: 12,
        message: 'Would reach 1173 of 1204 user(s); 31 opted out of email.',
      });
    }
    if (path === '/api/v1/admin/email/campaigns') {
      return json(route, {
        campaignId: 'send',
        dryRun: false,
        scanned: 1204,
        targeted: 1173,
        skippedOptedOut: 31,
        sent: 1173,
        failed: 0,
        truncated: false,
        quotaLimitPerDay: 100,
        quotaUsedToday: 12,
        message: 'Campaign sent to 1173 recipient(s); 31 opted out.',
      });
    }

    return json(route, {});
  });

  await page.route('**/health/**', async (route) => {
    if (mode === 'error') return json(route, { status: 'DOWN' }, 503);
    const path = new URL(route.request().url()).pathname;
    if (path === '/health/live') return json(route, HEALTH.live);
    if (path === '/health/ready') return json(route, HEALTH.ready);
    return json(route, HEALTH.base);
  });
}

/** Blocks the realtime socket so the suite is deterministic and offline-safe. */
export async function stubWebSocket(page: Page): Promise<void> {
  await page.addInitScript(() => {
    // A no-op stub keeps the app's socket lifecycle exercised (open → frames → close)
    // without a real backend handshake or reconnect noise in the screenshots.
    const Native = window.WebSocket;
    class StubWebSocket extends EventTarget {
      static CONNECTING = 0;
      static OPEN = 1;
      static CLOSING = 2;
      static CLOSED = 3;
      readonly CONNECTING = 0;
      readonly OPEN = 1;
      readonly CLOSING = 2;
      readonly CLOSED = 3;
      readyState = 1;
      onopen: ((event: Event) => void) | null = null;
      onclose: ((event: CloseEvent) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onmessage: ((event: MessageEvent) => void) | null = null;
      constructor(readonly url: string) {
        super();
        window.setTimeout(() => {
          this.onopen?.(new Event('open'));
          this.onmessage?.(
            new MessageEvent('message', {
              data: JSON.stringify({ type: 'connected', userId: '01234567-89ab-cdef-0123-456789abcdef' }),
            }),
          );
          this.onmessage?.(
            new MessageEvent('message', {
              data: JSON.stringify({ type: 'unread_count', count: 3 }),
            }),
          );
        }, 0);
      }
      send() {}
      close() {
        this.readyState = 3;
        this.onclose?.(new CloseEvent('close'));
      }
    }
    window.WebSocket = StubWebSocket as unknown as typeof Native;
  });
}

/** Sets the theme preference before any script runs. */
export async function setTheme(page: Page, theme: 'light' | 'dark'): Promise<void> {
  await page.addInitScript((value) => {
    window.localStorage.setItem('rtns.theme-preference', value as string);
  }, theme);
}

/**
 * Hides the WebGL hero canvas before a **full-page** capture.
 *
 * Chromium stitches a full-page screenshot by scrolling and re-rendering, which forces
 * the canvas to redraw mid-capture. Two consecutive captures of the same page differed
 * by ~3% of pixels, so `toHaveScreenshot` failed with "failed to take two consecutive
 * stable screenshots" — flaky in a way that looked like a product regression.
 *
 * Only the fullPage captures need this. The shader itself is covered by the
 * viewport-only `landing-hero` baseline, which is stable.
 *
 * The canvas is hidden rather than removed, so the CSS gradient fallback shows in the
 * full-page baseline — which is also exactly what a visitor without WebGL support
 * sees, so it is worth having under regression test.
 */
export async function freezeHeroCanvas(page: Page): Promise<void> {
  await page.addStyleTag({
    content: '[data-testid="shader-canvas"] { display: none !important; }',
  });
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
  );
}

/** Waits for fonts and the network to settle so screenshots are never half-painted. */
export async function settle(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle').catch(() => {
    /* the realtime socket keeps the connection open; networkidle may not settle */
  });
  await page.evaluate(() => document.fonts.ready);
  // One extra frame so GSAP's final `fromTo` state has been painted.
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
  );
}
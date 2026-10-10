import type {
  AuthResponse,
  NotificationItem,
  Preference,
  AdminStats,
  TemplateItem,
  HealthBase,
  HealthLive,
  HealthReady,
  MassEmailRequest,
  MassEmailResponse,
  EmailServiceBudget,
} from '@/types/api';
import { apiRequest } from './api-client';

/**
 * Auth (AuthController). Public endpoints opt out of the Authorization header.
 */
export const authApi = {
  register: (email: string, password: string, displayName: string) =>
    apiRequest<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      authenticated: false,
      body: { email, password, displayName },
    }),

  login: (email: string, password: string) =>
    apiRequest<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      authenticated: false,
      body: { email, password },
    }),
};

/**
 * Notification inbox (NotificationController). Every call is scoped to the caller's own
 * user id server-side — the client never supplies a user id, so there is no way to
 * address another account's notifications.
 */
export const notificationsApi = {
  history: (page = 0, size = 20) =>
    apiRequest<NotificationItem[]>(`/api/v1/notifications?page=${page}&size=${size}`),

  unreadCount: () => apiRequest<{ unreadCount: number }>('/api/v1/notifications/unread-count'),

  markRead: (id: string) =>
    apiRequest<NotificationItem>(`/api/v1/notifications/${id}/read`, { method: 'PATCH' }),

  markUnread: (id: string) =>
    apiRequest<NotificationItem>(`/api/v1/notifications/${id}/unread`, { method: 'PATCH' }),

  /** Returns `{ markedRead: n }` — the number of rows the user just read. */
  markAllRead: () =>
    apiRequest<{ markedRead: number }>('/api/v1/notifications/read-all', { method: 'PATCH' }),

  remove: (id: string) => apiRequest<void>(`/api/v1/notifications/${id}`, { method: 'DELETE' }),
};

/**
 * Preferences (PreferenceController). Intent controls whether an event type generates a
 * notification; the rest control delivery.
 */
export const preferencesApi = {
  list: () => apiRequest<Preference[]>('/api/v1/preferences'),

  setChannel: (eventType: string, channel: string, enabled: boolean) =>
    apiRequest<Preference>(`/api/v1/preferences/${eventType}/channel`, {
      method: 'PUT',
      body: { channel, enabled },
    }),

  setQuietHours: (eventType: string, enabled: boolean, startHour: number, endHour: number) =>
    apiRequest<Preference>(`/api/v1/preferences/${eventType}/quiet-hours`, {
      method: 'PUT',
      body: { enabled, startHour, endHour },
    }),

  setIntent: (eventType: string, intent: Preference['intent']) =>
    apiRequest<Preference>(`/api/v1/preferences/${eventType}/intent`, {
      method: 'PUT',
      body: { intent },
    }),

  setPushEnabled: (eventType: string, enabled: boolean) =>
    apiRequest<Preference>(`/api/v1/preferences/${eventType}/push?enabled=${enabled}`, {
      method: 'PUT',
    }),

  setDigestCadence: (eventType: string, cadence: Preference['digestCadence']) =>
    apiRequest<Preference>(`/api/v1/preferences/${eventType}/digest`, {
      method: 'PUT',
      body: { cadence },
    }),

  setFrequencyCaps: (eventType: string, maxPushesPerDay: number, minHoursBetweenPushes: number) =>
    apiRequest<Preference>(`/api/v1/preferences/${eventType}/frequency-caps`, {
      method: 'PUT',
      body: { maxPushesPerDay, minHoursBetweenPushes },
    }),

  remove: (eventType: string) =>
    apiRequest<void>(`/api/v1/preferences/${eventType}`, { method: 'DELETE' }),
};

/** Admin-only endpoints (AdminController, TemplateController) — ROLE_ADMIN enforced server-side. */
export const adminApi = {
  stats: () => apiRequest<AdminStats>('/api/v1/admin/stats'),

  broadcast: (title: string, message: string, priority = 'MEDIUM') =>
    apiRequest<{ delivered: number }>('/api/v1/admin/broadcast', {
      method: 'POST',
      body: { title, message, priority },
    }),

  createTemplate: (input: {
    code: string;
    channel: string;
    subjectTemplate?: string;
    bodyTemplate: string;
    trafficPct?: number;
  }) =>
    apiRequest<TemplateItem>('/api/v1/admin/templates', {
      method: 'POST',
      body: {
        code: input.code,
        channel: input.channel,
        locale: 'en-US',
        subjectTemplate: input.subjectTemplate ?? null,
        bodyTemplate: input.bodyTemplate,
        trafficPct: input.trafficPct,
      },
    }),

  templateHistory: (code: string) => apiRequest<TemplateItem[]>(`/api/v1/admin/templates/${code}`),

  deliveryMetrics: () => apiRequest<Record<string, number>>('/api/v1/admin/delivery-metrics'),

  activeSessions: () =>
    apiRequest<{ activeWebSocketSessions: number }>('/api/v1/admin/active-sessions'),
};

/**
 * Health probes (HealthController). These are the only endpoints the browser can reach
 * without a session, which makes them the honest signal for the status page.
 */
export const healthApi = {
  base: () => apiRequest<HealthBase>('/health', { authenticated: false }),
  live: () => apiRequest<HealthLive>('/health/live', { authenticated: false }),
  ready: () => apiRequest<HealthReady>('/health/ready', { authenticated: false }),
};

/**
 * WebSocket URL for the live notification channel. The JWT travels as a query
 * parameter because the browser WebSocket API cannot set custom headers; the server
 * validates it during the handshake (WebSocketAuthInterceptor).
 */
export function realtimeUrl(): string {
  const token = (() => {
    try {
      return localStorage.getItem('rtns.token') ?? '';
    } catch {
      return '';
    }
  })();
  const override = import.meta.env.VITE_WS_URL as string | undefined;
  const base = override ?? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`;
  return `${base}/ws/notifications?token=${encodeURIComponent(token)}`;
}
/**
 * Mass-email campaigns (ROLE_ADMIN, MassEmailController).
 *
 * Preview first, always: `preview` reports the blast radius without sending. The backend
 * applies opt-out filtering, a recipient cap and a daily-quota refusal, and surfaces a
 * refusal as `blocked` + `blockedReason` rather than as an HTTP error — so a caller must
 * inspect `blocked`, not merely catch a rejection.
 */
export const massEmailApi = {
  /** Daily send budget of the upstream email service; best-effort. */
  quota: () => apiRequest<EmailServiceBudget>('/api/v1/admin/email/quota'),

  preview: (input: MassEmailRequest) =>
    apiRequest<MassEmailResponse>('/api/v1/admin/email/campaigns/preview', {
      method: 'POST',
      body: { ...input, variables: input.variables ?? {} },
    }),

  send: (input: MassEmailRequest) =>
    apiRequest<MassEmailResponse>('/api/v1/admin/email/campaigns', {
      method: 'POST',
      body: { ...input, variables: input.variables ?? {} },
    }),
};

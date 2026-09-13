// Typed REST client for the RTNS backend (see api controllers + DESIGN.md ex-data-table-cell).
const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? '';

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('rtns.token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(init.headers ?? {}) },
  });
  if (res.status === 204) return undefined as T;
  const body = (await res.json().catch(() => ({}))) as { message?: string } & T;
  if (!res.ok) throw new ApiError(res.status, body.message ?? `Request failed (${res.status})`);
  return body as T;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  displayName: string;
  roles: string[];
}

export const api = {
  register: (email: string, password: string, displayName: string) =>
    request<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName }),
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
};

export interface NotificationItem {
  id: string;
  eventType: string;
  channel: string;
  subject: string | null;
  body: string | null;
  status: string;
  read: boolean;
  createdAt: string;
  sentAt: string | null;
}

export const notificationsApi = {
  history: (page = 0, size = 20) =>
    request<NotificationItem[]>(`/api/v1/notifications?page=${page}&size=${size}`),
  unreadCount: () =>
    request<{ unreadCount: number }>('/api/v1/notifications/unread-count'),
  markRead: (id: string) =>
    request<NotificationItem>(`/api/v1/notifications/${id}/read`, { method: 'PATCH' }),
  markUnread: (id: string) =>
    request<NotificationItem>(`/api/v1/notifications/${id}/unread`, { method: 'PATCH' }),
  remove: (id: string) =>
    request<void>(`/api/v1/notifications/${id}`, { method: 'DELETE' }),
};

export interface Preference {
  eventType: string;
  channelOptIn: Record<string, boolean>;
  quietHoursEnabled: boolean;
  quietHoursStart: number;
  quietHoursEnd: number;
}

export const preferencesApi = {
  list: () => request<Preference[]>('/api/v1/preferences'),
  setChannel: (eventType: string, channel: string, enabled: boolean) =>
    request<Preference>(`/api/v1/preferences/${eventType}/channel`, {
      method: 'PUT',
      body: JSON.stringify({ channel, enabled }),
    }),
  setQuietHours: (eventType: string, enabled: boolean, startHour: number, endHour: number) =>
    request<Preference>(`/api/v1/preferences/${eventType}/quiet-hours`, {
      method: 'PUT',
      body: JSON.stringify({ enabled, startHour, endHour }),
    }),
};

export interface AdminStats {
  totalNotifications: number;
  totalUsers: number;
  notificationsToday: number;
  unreadNotifications: number;
  readRatePercentage: number;
}

export const adminApi = {
  stats: () => request<AdminStats>('/api/v1/admin/stats'),
  broadcast: (title: string, message: string, priority = 'MEDIUM') =>
    request<{ delivered: number }>('/api/v1/admin/broadcast', {
      method: 'POST',
      body: JSON.stringify({ title, message, priority }),
    }),
};

export function wsUrl(): string {
  const token = localStorage.getItem('rtns.token') ?? '';
  const base = (import.meta.env.VITE_WS_URL as string | undefined) ?? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`;
  return `${base}/ws/notifications?token=${encodeURIComponent(token)}`;
}

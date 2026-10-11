/**
 * Wire types mirroring the backend DTOs.
 * Sources:
 *   api/.../dto/auth/AuthResponse.java, dto/notification/NotificationResponse.java,
 *   dto/preference/PreferenceResponse.java, dto/template/TemplateResponse.java,
 *   controller/AdminController.java (AdminStatsResponse)
 */

export type RoleName = 'ADMIN' | 'SERVICE' | 'USER';

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  displayName: string;
  roles: RoleName[];
}

export type NotificationChannel = 'EMAIL' | 'SMS' | 'PUSH' | 'IN_APP' | 'WEBSOCKET';
export type NotificationStatus =
  | 'PENDING'
  | 'SCHEDULED'
  | 'PROCESSING'
  | 'SENT'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETRYING'
  | 'DEAD_LETTERED'
  | 'CANCELLED'
  | 'QUEUED_DIGEST';
export type NotificationPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface NotificationItem {
  id: string;
  eventType: string;
  channel: NotificationChannel;
  subject: string | null;
  body: string | null;
  status: NotificationStatus;
  /** NotificationResponse.priority (V3 migration; serialized as a string). */
  priority: NotificationPriority;
  read: boolean;
  createdAt: string;
  sentAt: string | null;
}

export type NotificationIntent = 'ALL' | 'MENTIONS' | 'MUTE';
export type DigestCadence = 'OFF' | 'DAILY' | 'WEEKLY';

export interface Preference {
  eventType: string;
  channelOptIn: Partial<Record<NotificationChannel, boolean>>;
  quietHoursEnabled: boolean;
  quietHoursStart: number;
  quietHoursEnd: number;
  intent: NotificationIntent;
  pushEnabled: boolean;
  digestCadence: DigestCadence;
  maxPushesPerDay: number;
  minHoursBetweenPushes: number;
}

export interface AdminStats {
  totalNotifications: number;
  totalUsers: number;
  notificationsToday: number;
  unreadNotifications: number;
  readRatePercentage: number;
}

export interface TemplateItem {
  id: string;
  code: string;
  channel: NotificationChannel;
  version: number;
  subjectTemplate: string | null;
  bodyTemplate: string;
  locale: string;
  active: boolean;
  createdAt: string;
  trafficPct: number;
}

/** Typed realtime frames pushed over the WebSocket (RedisRealtimeSubscriber + handler). */
export type RealtimeFrame =
  | { type: 'connected'; userId: string }
  | { type: 'pong' }
  | { type: 'unread_count'; count: number }
  | {
      type: 'notification';
      notification: {
        notificationId: string;
        eventType: string;
        subject: string;
        body: string;
        createdAt: string;
      };
    };

/*
 * Health probe shapes (HealthController). Deliberately typed to the literal responses:
 * the failure paths omit keys (`ready` has no `readiness` on 503), so every field the
 * UI reads is optional rather than lying about what a down service returns.
 */
export interface HealthBase {
  status: string;
  service: string;
}

export interface HealthLive {
  status: string;
  liveness?: string;
}

export interface HealthReady {
  status: string;
  readiness?: string;
  database?: string;
}
/*
 * Mass-email campaign wire types (MassEmailController).
 * `blocked` is the field to read first: it carries the machine-readable reason a send was
 * refused, rather than failing the request with an opaque error.
 */
export interface MassEmailRequest {
  subject: string;
  body: string;
  variables?: Record<string, string>;
  priority?: NotificationPriority;
}

export interface MassEmailResponse {
  campaignId: string | null;
  dryRun: boolean;
  scanned: number;
  targeted: number;
  skippedOptedOut: number;
  sent: number;
  failed: number;
  truncated: boolean;
  blocked?: boolean | null;
  blockedReason?: string | null;
  quotaLimitPerDay?: number | null;
  quotaUsedToday?: number | null;
  message?: string | null;
}

/** Daily send budget of the external email service. */
export interface EmailServiceBudget {
  used: number | null;
  limit: number | null;
  reachable: boolean;
  remaining: number;
}

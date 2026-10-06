import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bell,
  Info,
  Mail,
  MessageSquare,
  Radio,
  ShieldCheck,
  Smartphone,
  type LucideIcon,
} from 'lucide-react';
import { preferencesApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import type { DigestCadence, NotificationChannel, NotificationIntent, Preference } from '@/types/api';
import { PageHeader } from '@/components/layout/primitives';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';

import { PreferencesSkeleton } from '@/components/ui/skeletons';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { toast } from '@/components/ui/toaster';

/** Mirrors EventChannelRouting on the backend: which channels each event type uses. */
const EVENT_TYPES: { value: string; label: string; security: boolean; channels: string[] }[] = [
  { value: 'ORDER_PLACED', label: 'Order placed', security: false, channels: ['EMAIL', 'PUSH', 'IN_APP'] },
  { value: 'ORDER_DELIVERED', label: 'Order delivered', security: false, channels: ['PUSH', 'IN_APP', 'SMS'] },
  { value: 'PAYMENT_SUCCESS', label: 'Payment succeeded', security: false, channels: ['EMAIL', 'IN_APP'] },
  { value: 'COMMENT_ADDED', label: 'Comment added', security: false, channels: ['PUSH', 'IN_APP', 'WEBSOCKET'] },
  { value: 'LIKE_RECEIVED', label: 'Like received', security: false, channels: ['IN_APP', 'WEBSOCKET'] },
  { value: 'MENTIONED', label: 'Mentioned', security: false, channels: ['PUSH', 'IN_APP', 'WEBSOCKET'] },
  { value: 'PASSWORD_RESET', label: 'Password reset', security: true, channels: ['EMAIL', 'SMS'] },
  { value: 'OTP_GENERATED', label: 'One-time code', security: true, channels: ['SMS', 'EMAIL'] },
];

const ALL_CHANNELS = ['EMAIL', 'SMS', 'PUSH', 'IN_APP', 'WEBSOCKET'];

const CHANNEL_ICON: Record<string, LucideIcon> = {
  EMAIL: Mail,
  SMS: MessageSquare,
  PUSH: Smartphone,
  IN_APP: Bell,
  WEBSOCKET: Radio,
};

const INTENTS: { value: NotificationIntent; label: string; help: string }[] = [
  { value: 'ALL', label: 'All activity', help: 'Notify me for every event of this type.' },
  { value: 'MENTIONS', label: 'Only mentions', help: 'Direct activity only: mentions, replies and security codes.' },
  { value: 'MUTE', label: 'Mute', help: 'Stop generating notifications for this type entirely.' },
];

const CADENCES: { value: DigestCadence; label: string }[] = [
  { value: 'OFF', label: 'Off' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
];

/**
 * The backend binds quiet hours as `@Min(0) @Max(23)` ints, and a non-numeric or
 * out-of-range value deserialises to 0 rather than erroring — so the client clamps
 * rather than trusting the input, and falls back to the value already on the server.
 */
function clampHour(raw: string): number {
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) return 0;
  return Math.min(23, Math.max(0, parsed));
}

function hourLabel(hour: number): string {
  const suffix = hour < 12 ? 'am' : 'pm';
  const normalized = hour % 12 === 0 ? 12 : hour % 12;
  return `${normalized}${suffix}`;
}

export function Preferences() {
  const [prefs, setPrefs] = useState<Preference[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [savingKey, setSavingKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPrefs(await preferencesApi.list());
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const byEvent = useMemo(() => new Map(prefs.map((pref) => [pref.eventType, pref])), [prefs]);

  /**
   * Single write path for every control. The control disables itself while its request is
   * in flight (optimistic only where it's safe — i.e. a single boolean), and a failure
   * reloads authoritative state so the UI never drifts from the server.
   */
  const apply = async (key: string, task: () => Promise<Preference>, successMessage?: string) => {
    setSavingKey(key);
    try {
      const updated = await task();
      setPrefs((current) => [...current.filter((pref) => pref.eventType !== updated.eventType), updated]);
      if (successMessage) toast.success(successMessage);
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      toast.error(normalized.title, { description: normalized.message });
    } finally {
      setSavingKey(null);
    }
  };

  const renderEventCard = (meta: (typeof EVENT_TYPES)[number]) => {
    const pref = byEvent.get(meta.value);
    const busyKey = `${meta.value}:`;

    return (
      <Card key={meta.value}>
        <CardHeader className="flex-row flex-wrap items-start justify-between gap-2 space-y-0">
          <div className="min-w-0">
            <CardTitle>{meta.label}</CardTitle>
            <p className="mt-0.5 font-mono text-xs text-muted-foreground">{meta.value}</p>
          </div>
          <div className="flex flex-wrap items-center gap-1.5">
            {meta.security ? (
              <Badge variant="warning">
                <ShieldCheck aria-hidden="true" />
                Security
              </Badge>
            ) : null}
            {!pref ? <Badge variant="muted">Defaults</Badge> : null}
          </div>
        </CardHeader>

        <CardContent className="space-y-5">
          {/* 1. Intent */}
          <fieldset disabled={meta.security} className="space-y-2 disabled:opacity-70">
            <legend className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Should this notify you?
            </legend>
            <div className="flex flex-wrap gap-2">
              {INTENTS.map((option) => {
                const active = (pref?.intent ?? 'ALL') === option.value;
                return (
                  <Button
                    key={option.value}
                    size="sm"
                    variant={active ? 'default' : 'outline'}
                    disabled={meta.security || savingKey === `${busyKey}intent`}
                    title={meta.security ? 'Security alerts always deliver' : option.help}
                    onClick={() =>
                      void apply(
                        `${busyKey}intent`,
                        () => preferencesApi.setIntent(meta.value, option.value),
                        `Intent set to ${option.value.toLowerCase()}`,
                      )
                    }
                  >
                    {option.label}
                  </Button>
                );
              })}
            </div>
            <p className="text-xs text-muted-foreground">
              {INTENTS.find((option) => option.value === (pref?.intent ?? 'ALL'))?.help}
            </p>
            {meta.security ? (
              <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
                <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                Security events always notify — muting them could lock you out of your account.
              </p>
            ) : null}
          </fieldset>

          {/* 2. Channels */}
          <fieldset disabled={savingKey?.startsWith(busyKey) ?? false} className="space-y-2">
            <legend className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Channels
            </legend>
            <div className="flex flex-wrap gap-2">
              {ALL_CHANNELS.map((channel) => {
                const Icon = CHANNEL_ICON[channel] ?? Bell;
                // Channels this event never routes to are shown as inert, not hidden, so
                // the routing model is legible instead of mysterious.
                const routed = meta.channels.includes(channel);
                const enabled = pref?.channelOptIn[channel as NotificationChannel] ?? true;
                return (
                  <Button
                    key={channel}
                    size="sm"
                    variant={enabled && routed ? 'secondary' : 'ghost'}
                    disabled={!routed || savingKey === `${busyKey}channel:${channel}`}
                    title={routed ? undefined : `${meta.value} does not route to ${channel}`}
                    className={routed ? undefined : 'opacity-45'}
                    onClick={() =>
                      void apply(
                        `${busyKey}channel:${channel}`,
                        () => preferencesApi.setChannel(meta.value, channel, !enabled),
                      )
                    }
                  >
                    <Icon aria-hidden="true" />
                    {channel}
                    {!routed ? <span className="sr-only">(not used by this event)</span> : null}
                  </Button>
                );
              })}
            </div>
          </fieldset>

          {pref ? (
            <div className="grid gap-5 border-t border-border pt-5 sm:grid-cols-2 xl:grid-cols-4">
              {/* 3. Push switch */}
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Push delivery
                </p>
                <div className="mt-2.5 flex items-center gap-2.5">
                  <Switch
                    id={`${meta.value}-push`}
                    checked={pref.pushEnabled}
                    disabled={savingKey === `${busyKey}push`}
                    onCheckedChange={(checked) =>
                      void apply(
                        `${busyKey}push`,
                        () => preferencesApi.setPushEnabled(meta.value, checked),
                      )
                    }
                  />
                  <Label htmlFor={`${meta.value}-push`} className="cursor-pointer">
                    {pref.pushEnabled ? 'Enabled' : 'Disabled'}
                  </Label>
                </div>
                <p className="mt-1.5 text-xs text-muted-foreground">
                  Push is additionally bounded by the frequency cap.
                </p>
              </div>

              {/* 4. Quiet hours */}
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Quiet hours
                </p>
                <div className="mt-2.5 flex items-center gap-2.5">
                  <Switch
                    id={`${meta.value}-quiet`}
                    checked={pref.quietHoursEnabled}
                    disabled={meta.security || savingKey === `${busyKey}quiet`}
                    onCheckedChange={(checked) =>
                      void apply(
                        `${busyKey}quiet`,
                        () =>
                          preferencesApi.setQuietHours(
                            meta.value,
                            checked,
                            pref.quietHoursStart,
                            pref.quietHoursEnd,
                          ),
                        checked ? 'Quiet hours on' : 'Quiet hours off',
                      )
                    }
                  />
                  <Label htmlFor={`${meta.value}-quiet`} className="cursor-pointer">
                    {pref.quietHoursEnabled ? 'On' : 'Off'}
                  </Label>
                </div>
                {pref.quietHoursEnabled ? (
                  <div className="mt-2.5 flex flex-wrap items-center gap-2">
                    <Label htmlFor={`${meta.value}-quiet-start`} className="text-muted-foreground">
                      From
                    </Label>
                    <Input
                      id={`${meta.value}-quiet-start`}
                      type="number"
                      min={0}
                      max={23}
                      className="h-8 w-16"
                      defaultValue={pref.quietHoursStart}
                      disabled={savingKey === `${busyKey}quiet`}
                      onBlur={(event) => {
                        const value = clampHour(event.target.value);
                        if (value === pref.quietHoursStart) return;
                        void apply(
                          `${busyKey}quiet`,
                          () =>
                            preferencesApi.setQuietHours(
                              meta.value,
                              true,
                              value,
                              pref.quietHoursEnd,
                            ),
                          'Quiet hours updated',
                        );
                      }}
                    />
                    <Label htmlFor={`${meta.value}-quiet-end`} className="text-muted-foreground">
                      to
                    </Label>
                    <Input
                      id={`${meta.value}-quiet-end`}
                      type="number"
                      min={0}
                      max={23}
                      className="h-8 w-16"
                      defaultValue={pref.quietHoursEnd}
                      disabled={savingKey === `${busyKey}quiet`}
                      onBlur={(event) => {
                        const value = clampHour(event.target.value);
                        if (value === pref.quietHoursEnd) return;
                        void apply(
                          `${busyKey}quiet`,
                          () =>
                            preferencesApi.setQuietHours(
                              meta.value,
                              true,
                              pref.quietHoursStart,
                              value,
                            ),
                          'Quiet hours updated',
                        );
                      }}
                    />
                    <span className="text-xs text-muted-foreground">
                      {hourLabel(pref.quietHoursStart)}–{hourLabel(pref.quietHoursEnd)} server time
                    </span>
                  </div>
                ) : (
                  <p className="mt-1.5 text-xs text-muted-foreground">
                    Hold back notifications during the hours you choose.
                  </p>
                )}
                {meta.security ? (
                  <p className="mt-1.5 text-xs text-muted-foreground">
                    Security alerts ignore quiet hours.
                  </p>
                ) : null}
              </div>

              {/* 5. Frequency caps */}
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Frequency caps
                </p>
                <div className="mt-2 flex items-center gap-2">
                  <Label htmlFor={`${meta.value}-max`} className="text-muted-foreground">
                    max/day
                  </Label>
                  <Input
                    id={`${meta.value}-max`}
                    type="number"
                    min={0}
                    max={50}
                    className="h-8 w-20"
                    defaultValue={pref.maxPushesPerDay}
                    disabled={savingKey === `${busyKey}caps`}
                    onBlur={(event) => {
                      const value = Number(event.target.value);
                      if (value === pref.maxPushesPerDay) return;
                      void apply(
                        `${busyKey}caps`,
                        () => preferencesApi.setFrequencyCaps(meta.value, value, pref.minHoursBetweenPushes),
                        'Frequency cap updated',
                      );
                    }}
                  />
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <Label htmlFor={`${meta.value}-gap`} className="text-muted-foreground">
                    min gap (h)
                  </Label>
                  <Input
                    id={`${meta.value}-gap`}
                    type="number"
                    min={0}
                    max={168}
                    className="h-8 w-20"
                    defaultValue={pref.minHoursBetweenPushes}
                    disabled={savingKey === `${busyKey}caps`}
                    onBlur={(event) => {
                      const value = Number(event.target.value);
                      if (value === pref.minHoursBetweenPushes) return;
                      void apply(
                        `${busyKey}caps`,
                        () => preferencesApi.setFrequencyCaps(meta.value, pref.maxPushesPerDay, value),
                        'Frequency cap updated',
                      );
                    }}
                  />
                </div>
                <p className="mt-1.5 text-xs text-muted-foreground">
                  Over budget is deferred, never dropped.
                </p>
              </div>

              {/* 6. Digest */}
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Email digest
                </p>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {CADENCES.map((cadence) => (
                    <Button
                      key={cadence.value}
                      size="sm"
                      variant={pref.digestCadence === cadence.value ? 'default' : 'outline'}
                      disabled={meta.security || savingKey === `${busyKey}digest`}
                      title={meta.security ? 'Security alerts are never digested' : undefined}
                      onClick={() =>
                        void apply(
                          `${busyKey}digest`,
                          () => preferencesApi.setDigestCadence(meta.value, cadence.value),
                          `Digest cadence: ${cadence.label.toLowerCase()}`,
                        )
                      }
                    >
                      {cadence.label}
                    </Button>
                  ))}
                </div>
                <p className="mt-1.5 text-xs text-muted-foreground">
                  Batches low-value email. In-app stays instant.
                </p>
              </div>
            </div>
          ) : (
            <p className="border-t border-border pt-4 text-sm text-muted-foreground">
              Using defaults: all routed channels enabled, push limited to 2 per day with an 8 hour
              gap, and email digests off. Change any control to create an explicit rule.
            </p>
          )}
        </CardContent>
      </Card>
    );
  };

  const engagement = EVENT_TYPES.filter((meta) => !meta.security);
  const security = EVENT_TYPES.filter((meta) => meta.security);

  return (
    <>
      <PageHeader
        eyebrow="Settings"
        title="Notifications & delivery"
        description="Intent decides what generates a notification. Delivery decides how it reaches you."
        actions={
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
            Refresh
          </Button>
        }
      />

      {error ? (
        <div className="mb-5">
          <ErrorState
            kind={error.kind}
            message={error.message}
            onRetry={() => void load()}
            action={
              error.kind === 'auth'
                ? { label: 'Sign in again', onClick: () => window.location.assign('/login') }
                : undefined
            }
          />
        </div>
      ) : null}

      {loading ? (
        <PreferencesSkeleton cards={4} />
      ) : (
        <Tabs defaultValue="engagement">
          <TabsList aria-label="Preference sections">
            <TabsTrigger value="engagement">Engagement</TabsTrigger>
            <TabsTrigger value="security">Security</TabsTrigger>
          </TabsList>

          <TabsContent value="engagement" className="space-y-4">
            {engagement.map(renderEventCard)}
          </TabsContent>

          <TabsContent value="security" className="space-y-4">
            <div className="flex items-start gap-2.5 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
              <p className="text-pretty text-warning-foreground dark:text-foreground">
                Security events (password reset and one-time codes) always notify on every channel
                you have not disabled. Intent and digest controls are intentionally locked here.
              </p>
            </div>
            {security.map(renderEventCard)}
          </TabsContent>
        </Tabs>
      )}
    </>
  );
}
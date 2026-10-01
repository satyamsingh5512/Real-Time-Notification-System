import { useCallback, useEffect, useState } from 'react';
import {
  Activity,
  Bell,
  BellOff,
  CheckCheck,
  Gauge,
  Radio,
  TrendingUp,
  Users,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { adminApi, notificationsApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import { useAuth } from '@/stores/auth';
import { useRealtime } from '@/hooks/useRealtime';
import type { NotificationItem } from '@/types/api';
import { MetricCard, PageHeader, RevealGroup } from '@/components/layout/primitives';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Skeleton, SkeletonStat, SkeletonText } from '@/components/ui/skeleton';
import { NotificationRow } from '@/components/notifications/NotificationRow';

interface Stats {
  totalNotifications: number | null;
  unread: number | null;
  recent: NotificationItem[];
}

/**
 * Workspace overview (spec §18). Every number comes from the live API — there are no
 * placeholder or fabricated metrics anywhere on this page.
 */
export function Dashboard() {
  const { user, isAdmin } = useAuth();
  const { connected } = useRealtime();

  const [stats, setStats] = useState<Stats>({ totalNotifications: null, unread: null, recent: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [history, unreadCount] = await Promise.all([
        notificationsApi.history(0, 6),
        notificationsApi.unreadCount(),
      ]);
      setStats({
        totalNotifications: history.length,
        unread: unreadCount.unreadCount,
        recent: history,
      });
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // Refresh the recent list whenever a realtime frame arrives, so the page stays live.
  useEffect(() => {
    if (connected) void load();
  }, [connected, load]);

  const markRead = async (id: string) => {
    setBusyId(id);
    try {
      const updated = await notificationsApi.markRead(id);
      setStats((current) => ({
        ...current,
        unread: current.unread === null ? null : Math.max(0, current.unread - 1),
        recent: current.recent.map((item) => (item.id === id ? updated : item)),
      }));
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setBusyId(null);
    }
  };

  const markAllRead = async () => {
    try {
      await notificationsApi.markAllRead();
      setStats((current) => ({
        ...current,
        unread: 0,
        recent: current.recent.map((item) => ({ ...item, read: true })),
      }));
    } catch (caught) {
      setError(normalizeError(caught));
    }
  };

  const firstName = user?.displayName?.split(' ')[0] || user?.email?.split('@')[0] || 'there';

  return (
    <>
      <PageHeader
        eyebrow="Workspace"
        title={`Welcome back, ${firstName}`}
        description="Live delivery overview for your account. Updates stream in without a refresh."
        actions={
          <>
            <Button variant="outline" size="sm" asChild>
              <Link to="/preferences">Delivery preferences</Link>
            </Button>
            <Button size="sm" onClick={() => void markAllRead()} disabled={!stats.unread}>
              <CheckCheck aria-hidden="true" />
              Mark all read
            </Button>
          </>
        }
      />

      {error ? (
        <div className="mb-6">
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

      {/* Metric cards: count up once when data lands, never continuously */}
      <RevealGroup className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {loading ? (
          Array.from({ length: 4 }).map((_, index) => <SkeletonStat key={index} />)
        ) : (
          <>
            <MetricCard
              index={0}
              label="Unread"
              value={stats.unread}
              icon={<Bell />}
              tone="primary"
              hint={stats.unread ? 'Needs your attention' : 'All caught up'}
            />
            <MetricCard
              index={1}
              label="Recent activity"
              value={stats.totalNotifications}
              icon={<TrendingUp />}
              hint="Notifications in the current page"
            />
            <MetricCard
              index={2}
              label="Live channel"
              value={connected ? 1 : 0}
              icon={<Radio />}
              tone={connected ? 'success' : 'warning'}
              hint={connected ? 'WebSocket connected' : 'Reconnecting…'}
            />
            <MetricCard
              index={3}
              label="Roles granted"
              value={user?.roles.length ?? 0}
              icon={<Users />}
              hint={user?.roles.join(' · ') || 'No roles'}
            />
          </>
        )}
      </RevealGroup>

      <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <Card>
          <CardHeader className="flex-row items-center justify-between gap-3 space-y-0">
            <div>
              <CardTitle>Latest notifications</CardTitle>
              <p className="mt-0.5 text-sm text-muted-foreground">
                Newest first. Unread items are marked.
              </p>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/inbox">View all</Link>
            </Button>
          </CardHeader>
          <CardContent className="px-0">
            {loading ? (
              <div className="space-y-3 border-t border-border p-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={index} className="flex gap-3">
                    <Skeleton className="mt-1 size-2 rounded-full" />
                    <div className="flex-1 space-y-2">
                      <Skeleton className="h-3.5 w-2/3" />
                      <Skeleton className="h-3 w-full" />
                    </div>
                  </div>
                ))}
              </div>
            ) : stats.recent.length === 0 ? (
              <div className="p-4">
                <EmptyState
                  icon={<BellOff />}
                  title="Nothing delivered yet"
                  description="When an event is published for your account it will appear here within seconds. You can also trigger one from the admin console."
                  action={isAdmin ? { label: 'Open admin console', onClick: () => window.location.assign('/admin') } : undefined}
                  compact
                />
              </div>
            ) : (
              <div className="border-t border-border">
                {stats.recent.map((item) => (
                  <NotificationRow
                    key={item.id}
                    item={item}
                    onMarkRead={(id) => void markRead(id)}
                    busy={busyId === item.id}
                  />
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>How delivery works here</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {[
                {
                  icon: Activity,
                  title: 'Intent before delivery',
                  body: 'Choose whether an event type notifies you at all, then tune how it arrives.',
                },
                {
                  icon: Gauge,
                  title: 'Frequency caps',
                  body: 'Push budgets defer over-limit notifications instead of dropping them.',
                },
                {
                  icon: Bell,
                  title: 'Digests, not spam',
                  body: 'Low-value email is batched into one grouped message.',
                },
              ].map((item) => (
                <div key={item.title} className="flex gap-2.5">
                  <span
                    aria-hidden="true"
                    className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary"
                  >
                    <item.icon className="size-3.5" />
                  </span>
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{item.title}</p>
                    <p className="mt-0.5 text-pretty text-xs text-muted-foreground">{item.body}</p>
                  </div>
                </div>
              ))}
              <Button variant="outline" size="sm" className="mt-1 w-full" asChild>
                <Link to="/preferences">Configure preferences</Link>
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Session</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <SkeletonText lines={3} />
              ) : (
                <dl className="space-y-2 text-sm">
                  {[
                    { label: 'Email', value: user?.email ?? '—' },
                    { label: 'Display name', value: user?.displayName ?? '—' },
                    {
                      label: 'Roles',
                      value: (
                        <span className="mt-1 flex flex-wrap gap-1">
                          {(user?.roles ?? []).map((role) => (
                            <Badge key={role} variant={role === 'ADMIN' ? 'default' : 'muted'}>
                              {role}
                            </Badge>
                          ))}
                        </span>
                      ),
                    },
                  ].map((row) => (
                    <div key={row.label} className="flex items-start justify-between gap-3">
                      <dt className="text-muted-foreground">{row.label}</dt>
                      <dd className="min-w-0 truncate text-right font-medium">{row.value}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}

/** Admin analytics strip, rendered only for ROLE_ADMIN. */
export function AdminMetrics() {
  const [stats, setStats] = useState<Awaited<ReturnType<typeof adminApi.stats>> | null>(null);
  const [error, setError] = useState<NormalizedError | null>(null);

  useEffect(() => {
    adminApi
      .stats()
      .then(setStats)
      .catch((caught) => setError(normalizeError(caught)));
  }, []);

  if (error) return <ErrorState kind={error.kind} message={error.message} compact />;
  if (!stats) return <SkeletonStat />;

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <MetricCard label="Total notifications" value={stats.totalNotifications} icon={<Bell />} />
      <MetricCard label="Total users" value={stats.totalUsers} icon={<Users />} />
      <MetricCard label="Delivered today" value={stats.notificationsToday} icon={<TrendingUp />} tone="success" />
      <MetricCard label="Read rate" value={stats.readRatePercentage} suffix="%" icon={<Gauge />} />
    </div>
  );
}
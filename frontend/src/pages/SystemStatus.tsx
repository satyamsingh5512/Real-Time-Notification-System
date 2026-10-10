import { useCallback, useEffect, useState } from 'react';
import {
  Activity,
  CheckCircle2,
  Database,
  Radio,
  RefreshCw,
  Server,
  TriangleAlert,
  XCircle,
} from 'lucide-react';
import { adminApi, healthApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import { useAuth } from '@/stores/auth';
import { useRealtime } from '@/stores/realtime';
import { PageHeader } from '@/components/layout/primitives';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { SystemStatusSkeleton } from '@/components/ui/skeletons';

/**
 * System status (spec §25 in spirit — operational observability, built from real probes).
 *
 * This is deliberately NOT a fake audit-log table. There is no audit endpoint in this
 * backend, so instead of inventing rows the page reports the three things that are
 * genuinely observable: the liveness and readiness probes, the browser's own live
 * WebSocket state, and — for admins — the real delivery-status counters.
 *
 * Probes are `authenticated: false`, so they work even when a session has lapsed, which
 * makes this the right landing page when the API is down.
 */
type ProbeState = 'unknown' | 'up' | 'down';

interface Probe {
  label: string;
  detail: string;
  state: ProbeState;
  icon: typeof Server;
}

export function SystemStatus() {
  const { isAdmin } = useAuth();
  const { connected } = useRealtime();

  const [probes, setProbes] = useState<Probe[]>([]);
  const [delivery, setDelivery] = useState<Record<string, number> | null>(null);
  const [sessions, setSessions] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [lastChecked, setLastChecked] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      // Probes run in parallel and settle independently: a down readiness probe must
      // not hide a healthy liveness probe.
      const [live, ready, adminStats, metrics, activeSessions] = await Promise.allSettled([
        healthApi.live(),
        healthApi.ready(),
        isAdmin ? adminApi.stats() : Promise.reject(new Error('not-admin')),
        isAdmin ? adminApi.deliveryMetrics() : Promise.reject(new Error('not-admin')),
        isAdmin ? adminApi.activeSessions() : Promise.reject(new Error('not-admin')),
      ]);

      const state = (result: PromiseSettledResult<unknown>) =>
        result.status === 'fulfilled' ? 'up' : 'down';

      setProbes([
        {
          label: 'Liveness',
          detail:
            live.status === 'fulfilled'
              ? (live.value.liveness ?? live.value.status)
              : 'Probe failed to respond',
          state: state(live),
          icon: Activity,
        },
        {
          label: 'Readiness',
          detail:
            ready.status === 'fulfilled'
              ? [
                  ready.value.readiness ?? ready.value.status,
                  ready.value.database ? `Database ${ready.value.database}` : null,
                ]
                  .filter(Boolean)
                  .join(' · ')
              : 'Service or database is not accepting traffic',
          state: state(ready),
          icon: Database,
        },
        {
          label: 'Live channel',
          detail: connected ? 'WebSocket connected' : 'Disconnected — reconnecting',
          state: connected ? 'up' : 'down',
          icon: Radio,
        },
      ]);

      if (isAdmin) {
        setDelivery(
          metrics.status === 'fulfilled'
            ? metrics.value
            : adminStats.status === 'fulfilled'
              ? null
              : null,
        );
        setSessions(activeSessions.status === 'fulfilled' ? activeSessions.value.activeWebSocketSessions : null);
      }

      // Only surface an error when the user is *also* an admin and admin calls failed —
      // for a normal user the probes are the whole page.
      if (isAdmin && adminStats.status === 'rejected' && metrics.status === 'rejected') {
        setError(normalizeError(adminStats.reason));
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
      setLastChecked(new Date());
    }
  }, [isAdmin, connected]);

  useEffect(() => {
    void load();
  }, [load]);

  const anyDown = probes.some((probe) => probe.state === 'down');
  const allUp = probes.length > 0 && probes.every((probe) => probe.state === 'up');

  const statusTone = loading
    ? 'muted'
    : allUp
      ? 'success'
      : anyDown
        ? 'warning'
        : 'muted';

  const statusLabel = loading
    ? 'Checking…'
    : allUp
      ? 'All systems operational'
      : anyDown
        ? 'Degraded'
        : 'Unknown';

  return (
    <>
      <PageHeader
        eyebrow="Operations"
        title="System status"
        description="Live health of the delivery pipeline, checked directly against the API."
        actions={
          <Button variant="outline" size="sm" onClick={() => void load()} loading={refreshing} loadingText="Checking…">
            {!refreshing ? <RefreshCw aria-hidden="true" /> : null}
            Re-check
          </Button>
        }
      />

      {loading ? (
        <SystemStatusSkeleton />
      ) : (
        <div className="space-y-4">
          {error ? (
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
          ) : null}

          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={statusTone}>
              {allUp ? (
                <CheckCircle2 aria-hidden="true" />
              ) : anyDown ? (
                <TriangleAlert aria-hidden="true" />
              ) : (
                <Server aria-hidden="true" />
              )}
              {statusLabel}
            </Badge>
            {lastChecked ? (
              <span className="text-xs text-muted-foreground">
                Checked {lastChecked.toLocaleTimeString(undefined, { timeStyle: 'medium' })}
              </span>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            {probes.map((probe) => (
              <Card key={probe.label}>
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                      {probe.label}
                    </p>
                    <span
                      className={
                        probe.state === 'up'
                          ? 'text-success'
                          : probe.state === 'down'
                            ? 'text-destructive'
                            : 'text-muted-foreground'
                      }
                    >
                      {probe.state === 'up' ? (
                        <CheckCircle2 className="size-4" aria-hidden="true" />
                      ) : (
                        <XCircle className="size-4" aria-hidden="true" />
                      )}
                    </span>
                  </div>
                  {/* State is carried by the icon AND the text, never colour alone (§6). */}
                  <p className="mt-2 text-sm font-medium">
                    <span className="sr-only">{probe.state === 'up' ? 'Operational: ' : 'Failing: '}</span>
                    {probe.detail}
                  </p>
                  <p className="mt-1 text-xs uppercase tracking-wide text-muted-foreground">
                    {probe.state === 'up' ? 'Operational' : probe.state === 'down' ? 'Failing' : 'Unknown'}
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>

          {isAdmin && delivery ? (
            <Card>
              <CardHeader>
                <CardTitle>Delivery pipeline</CardTitle>
                <CardDescription>
                  Notification counts by lifecycle state across the whole platform. This replaces
                  the audit-log view the design brief asked for — no audit trail is recorded by
                  this backend, so this is the real operational data instead.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {Object.entries(delivery)
                    .filter(([status]) => status !== 'QUEUED_DIGEST')
                    .map(([status, count]) => (
                      <div
                        key={status}
                        className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5"
                      >
                        <span className="font-mono text-xs uppercase tracking-wide text-muted-foreground">
                          {status.replace(/_/g, ' ')}
                        </span>
                        <span className="text-sm font-semibold tabular">{count.toLocaleString()}</span>
                      </div>
                    ))}
                </div>
                {sessions !== null ? (
                  <p className="mt-4 text-xs text-muted-foreground">
                    <strong className="font-medium text-foreground tabular">{sessions}</strong> live
                    WebSocket {sessions === 1 ? 'session' : 'sessions'} on this instance.
                  </p>
                ) : null}
              </CardContent>
            </Card>
          ) : null}
        </div>
      )}
    </>
  );
}
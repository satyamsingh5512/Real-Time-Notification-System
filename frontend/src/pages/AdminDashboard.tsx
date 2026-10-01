import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  BarChart3,
  FlaskConical,
  Gauge,
  Megaphone,
  Percent,
  Radio,
  Send,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { adminApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import { useRealtime } from '@/hooks/useRealtime';
import type { AdminStats, NotificationPriority, TemplateItem } from '@/types/api';
import { MetricCard, PageHeader, RevealGroup } from '@/components/layout/primitives';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { Skeleton, SkeletonStat } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { toast } from '@/components/ui/toaster';
import { RelativeTime } from '@/components/notifications/NotificationRow';

/**
 * Admin console (ROLE_ADMIN only — enforced by the server; this route is additionally
 * guarded in the router and hidden from navigation).
 *
 * Three real capabilities, all backed by existing endpoints: platform stats, an in-app
 * broadcast that lands in every user's live inbox, and gradual template rollout.
 */
export function AdminDashboard() {
  const { connected } = useRealtime();

  const [stats, setStats] = useState<AdminStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<NormalizedError | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setStats(await adminApi.stats());
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <PageHeader
        eyebrow="Administration"
        title="Admin console"
        description="Platform-wide delivery statistics, broadcasts and template rollout."
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
              error.kind === 'forbidden'
                ? { label: 'Return to dashboard', onClick: () => window.location.assign('/') }
                : undefined
            }
          />
        </div>
      ) : null}

      <RevealGroup className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {loading || !stats ? (
          Array.from({ length: 4 }).map((_, index) => <SkeletonStat key={index} />)
        ) : (
          <>
            <MetricCard label="Total notifications" value={stats.totalNotifications} icon={<BarChart3 />} />
            <MetricCard label="Registered users" value={stats.totalUsers} icon={<Users />} />
            <MetricCard
              label="Delivered today"
              value={stats.notificationsToday}
              icon={<Send />}
              tone="success"
            />
            <MetricCard
              label="Unread"
              value={stats.unreadNotifications}
              icon={<Gauge />}
              tone="warning"
            />
          </>
        )}
      </RevealGroup>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <BroadcastPanel connected={connected} />
        <TemplateRolloutPanel />
      </div>

      {stats ? (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle>Read rate</CardTitle>
            <CardDescription>
              Share of delivered notifications the recipient has opened.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-4">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-primary transition-[width] duration-500 ease-out-quart"
                  style={{ width: `${Math.max(0, Math.min(100, stats.readRatePercentage))}%` }}
                  role="progressbar"
                  aria-valuenow={Math.round(stats.readRatePercentage)}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label="Read rate percentage"
                />
              </div>
              <span className="w-14 text-right text-sm font-semibold tabular">
                {stats.readRatePercentage.toFixed(1)}%
              </span>
            </div>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}

/** Broadcast composer — POST /api/v1/admin/broadcast pushes to every user's inbox. */
function BroadcastPanel({ connected }: { connected: boolean }) {
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [priority, setPriority] = useState<NotificationPriority>('MEDIUM');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [confirming, setConfirming] = useState(false);

  const validate = () => {
    if (!title.trim()) return 'Enter a title.';
    if (!message.trim()) return 'Enter a message body.';
    return null;
  };

  const send = async () => {
    const problem = validate();
    if (problem) {
      setError({
        kind: 'validation',
        title: 'Check the broadcast',
        message: problem,
        fieldErrors: {},
        retryable: false,
        retryAfterSeconds: null,
      });
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await adminApi.broadcast(title.trim(), message.trim(), priority);
      toast.success('Broadcast delivered', {
        description: `${result.delivered} recipient(s) notified${connected ? ' and pushed live' : ''}.`,
      });
      setConfirming(false);
      setTitle('');
      setMessage('');
      setPriority('MEDIUM');
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      toast.error(normalized.title, { description: normalized.message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Megaphone className="size-4" aria-hidden="true" />
          Broadcast announcement
        </CardTitle>
        <CardDescription>
          Creates an in-app notification for every registered user and pushes it over their live
          connection where one is open.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error ? <ErrorState kind={error.kind} message={error.message} compact /> : null}
        <div className="space-y-1.5">
          <label htmlFor="broadcast-title" className="text-sm font-medium">
            Title
          </label>
          <Input
            id="broadcast-title"
            placeholder="Scheduled maintenance tonight"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            disabled={submitting}
          />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="broadcast-message" className="text-sm font-medium">
            Message
          </label>
          <textarea
            id="broadcast-message"
            rows={3}
            placeholder="Delivery may be delayed between 22:00 and 23:00 UTC."
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            disabled={submitting}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50"
          />
        </div>
        <fieldset disabled={submitting}>
          <legend className="text-sm font-medium">Priority</legend>
          <div className="mt-2 flex gap-2">
            {(['LOW', 'MEDIUM', 'HIGH'] as NotificationPriority[]).map((level) => (
              <Button
                key={level}
                size="sm"
                variant={priority === level ? 'default' : 'outline'}
                onClick={() => setPriority(level)}
              >
                {level.charAt(0) + level.slice(1).toLowerCase()}
              </Button>
            ))}
          </div>
          <p className="mt-1.5 text-xs text-muted-foreground">
            High priority skips digest batching and is never held for a digest window.
          </p>
        </fieldset>

        <Button
          onClick={() => setConfirming(true)}
          disabled={submitting || !title.trim() || !message.trim()}
        >
          <Send aria-hidden="true" />
          Review &amp; send
        </Button>
      </CardContent>

      <Dialog open={confirming} onOpenChange={(open_) => !open_ && setConfirming(false)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Send this broadcast to every user?</DialogTitle>
            <DialogDescription>
              This creates one in-app notification per registered user. It cannot be recalled.
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border border-border bg-muted/40 p-3 text-sm">
            <p className="font-medium">{title || '(no title)'}</p>
            <p className="mt-1 text-pretty text-muted-foreground">{message || '(no message)'}</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={() => void send()} loading={submitting} loadingText="Sending…">
              Send broadcast
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

/**
 * Gradual template rollout (DoorDash-style): publish a new version serving a percentage
 * of users via a stable hash, so copy changes ramp instead of cutting over at once.
 */
function TemplateRolloutPanel() {
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState('ORDER_PLACED');
  const [channel, setChannel] = useState('EMAIL');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [trafficPct, setTrafficPct] = useState(100);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [history, setHistory] = useState<TemplateItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const loadHistory = useCallback(async () => {
    if (!code.trim()) return;
    setHistoryLoading(true);
    try {
      setHistory(await adminApi.templateHistory(code.trim()));
    } catch {
      // A 404 simply means this code has no versions yet; the empty state covers it.
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  }, [code]);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  const publish = async () => {
    if (!body.trim()) {
      setError({
        kind: 'validation',
        title: 'Check the template',
        message: 'A body template is required.',
        fieldErrors: {},
        retryable: false,
        retryAfterSeconds: null,
      });
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await adminApi.createTemplate({
        code: code.trim(),
        channel,
        subjectTemplate: subject.trim() || undefined,
        bodyTemplate: body.trim(),
        trafficPct,
      });
      toast.success(`Template v${created.version} published`, {
        description:
          trafficPct >= 100
            ? 'Serving 100% of users immediately.'
            : `Serving ${trafficPct}% of users; the rest keep the previous version.`,
      });
      setOpen(false);
      setBody('');
      setSubject('');
      await loadHistory();
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      toast.error(normalized.title, { description: normalized.message });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <FlaskConical className="size-4" aria-hidden="true" />
          Template rollout
        </CardTitle>
        <CardDescription>
          Publish a new version for a percentage of users. Bucketing is a stable per-user hash, so
          nobody flip-flops between versions.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error ? <ErrorState kind={error.kind} message={error.message} compact /> : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1.5">
            <label htmlFor="template-code" className="text-sm font-medium">
              Template code
            </label>
            <Input
              id="template-code"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              disabled={submitting}
              placeholder="ORDER_PLACED"
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="template-channel" className="text-sm font-medium">
              Channel
            </label>
            <select
              id="template-channel"
              value={channel}
              onChange={(event) => setChannel(event.target.value)}
              disabled={submitting}
              className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
            >
              {['EMAIL', 'SMS', 'PUSH', 'IN_APP', 'WEBSOCKET'].map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <label htmlFor="template-traffic" className="shrink-0 text-sm font-medium">
            Rollout
          </label>
          <input
            id="template-traffic"
            type="range"
            min={0}
            max={100}
            step={5}
            value={trafficPct}
            onChange={(event) => setTrafficPct(Number(event.target.value))}
            disabled={submitting}
            className="h-2 flex-1 cursor-pointer appearance-none rounded-full bg-muted accent-[hsl(var(--primary))]"
          />
          <span className="flex w-20 items-center justify-end gap-1 text-sm font-medium tabular">
            <Percent className="size-3" aria-hidden="true" />
            {trafficPct}
          </span>
        </div>

        <Button onClick={() => setOpen(true)}>
          <FlaskConical aria-hidden="true" />
          Compose new version
        </Button>

        <div className="border-t border-border pt-4">
          <h3 className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            <Radio className="size-3" aria-hidden="true" />
            Version history
          </h3>
          {historyLoading ? (
            <div className="mt-3 space-y-2" aria-hidden="true">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-9 w-full" />
              ))}
            </div>
          ) : history.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">
              No versions published for <span className="font-mono">{code || 'this code'}</span> yet.
            </p>
          ) : (
            <div className="mt-3 overflow-hidden rounded-lg border border-border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Version</TableHead>
                    <TableHead>Traffic</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Created</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {history.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium tabular">v{item.version}</TableCell>
                      <TableCell className="tabular">{item.trafficPct}%</TableCell>
                      <TableCell>
                        {item.active ? (
                          <Badge variant="success">
                            <ShieldCheck aria-hidden="true" />
                            Active
                          </Badge>
                        ) : (
                          <Badge variant="muted">Superseded</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <RelativeTime iso={item.createdAt} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      </CardContent>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Publish template version</DialogTitle>
            <DialogDescription>
              Placeholders use double braces and are filled from the event payload, for example{' '}
              <code className="font-mono text-xs">{'{{orderId}}'}</code> or{' '}
              <code className="font-mono text-xs">{'{{amount}}'}</code>.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor="template-subject" className="text-sm font-medium">
                Subject <span className="font-normal text-muted-foreground">(email only)</span>
              </label>
              <Input
                id="template-subject"
                value={subject}
                onChange={(event) => setSubject(event.target.value)}
                disabled={submitting}
                placeholder="Order {{orderId}} confirmed"
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="template-body" className="text-sm font-medium">
                Body
              </label>
              <textarea
                id="template-body"
                rows={4}
                value={body}
                onChange={(event) => setBody(event.target.value)}
                disabled={submitting}
                placeholder="Thanks! Your order {{orderId}} for {{amount}} is being prepared."
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
              />
            </div>
            <p className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-2.5 text-xs text-warning-foreground dark:text-foreground">
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              Publishing at {trafficPct}% immediately serves that share of users the new copy. Lower
              the percentage if you want a cautious ramp.
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={() => void publish()} loading={submitting} loadingText="Publishing…">
              Publish v-next
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

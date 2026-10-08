import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Gauge, Mail, Send, Users } from 'lucide-react';
import { massEmailApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Textarea } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Progress } from '@/components/ui/progress';
import { ErrorState } from '@/components/ui/error-state';
import { toast } from '@/components/ui/toaster';

/**
 * Mass-email campaign composer (admin only).
 *
 * <p>Two deliberate safety properties of this form:
 *
 * <ol>
 *   <li><b>Preview before send.</b> The primary action is "Preview audience", which
 *       reports how many users would be reached and how many opted out <em>without
 *       sending anything</em>. An admin should never have to guess at blast radius
 *       before sending real mail to real people.</li>
 *   <li><b>Send is behind a confirmation dialog</b> that restates the recipient count
 *       from the preview, so the number being acted on is never re-derived by hand.</li>
 * </ol>
 *
 * <p>The backend independently enforces opt-out filtering, a size cap, and a refusal when
 * the upstream email service has spent its daily quota. This UI surfaces those outcomes
 * rather than hiding them.
 */
export function MassEmailPanel() {
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [priority, setPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM');
  /*
   * Optional idempotency key. Left blank, the backend generates a fresh campaign each
   * time. Setting it makes a re-send a per-recipient no-op — worth doing when a send
   * fails halfway and you want to retry the same campaign safely.
   */
  const [campaignId, setCampaignId] = useState('');

  const [quota, setQuota] = useState<{ used: number | null; limit: number | null; reachable: boolean } | null>(
    null,
  );
  const [preview, setPreview] = useState<MassEmailPreview | null>(null);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const loadQuota = useCallback(async () => {
    try {
      setQuota(await massEmailApi.quota());
    } catch {
      // Best-effort: an unknown budget must not block the panel.
      setQuota(null);
    }
  }, []);

  useEffect(() => {
    void loadQuota();
  }, [loadQuota]);

  const buildRequest = (): MassEmailRequestBody => ({
    subject,
    body,
    priority,
    ...(campaignId.trim() ? { campaignId: campaignId.trim() } : {}),
  });

  const runPreview = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await massEmailApi.preview(buildRequest());
      setPreview(result);
      if (result.blocked) {
        toast.error('Campaign blocked', { description: result.blockedReason ?? undefined });
      }
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setBusy(false);
    }
  };

  const confirmSend = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await massEmailApi.send(buildRequest());
      setConfirmOpen(false);
      if (result.blocked) {
        // The backend refused before sending anything. Show why rather than pretending.
        toast.error('Campaign not sent', { description: result.blockedReason ?? undefined });
        setPreview(result);
        return;
      }
      setPreview(result);
      toast.success(`Campaign sent to ${result.sent} recipient(s)`, {
        description:
          result.failed > 0
            ? `${result.failed} failed and are queued for retry on their notification rows.`
            : `${result.skippedOptedOut} recipient(s) had opted out of email.`,
      });
      void loadQuota();
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setBusy(false);
    }
  };

  const ready = subject.trim().length > 0 && body.trim().length > 0;
  const quotaUsed = quota?.used ?? null;
  const quotaLimit = quota?.limit ?? null;
  const quotaPct = quotaUsed !== null && quotaLimit ? Math.min(100, (quotaUsed / quotaLimit) * 100) : 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Mail className="size-4" aria-hidden="true" />
          Mass email
        </CardTitle>
        <CardDescription>
          Send a campaign to every user who has not opted out of email. Recipients who
          muted an event type or switched email off are excluded automatically.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        {error ? (
          <ErrorState kind={error.kind} message={error.message} onRetry={() => void runPreview()} compact />
        ) : null}

        {/*
          Upstream daily budget. The email service caps itself at 100 sends/day, so a
          campaign larger than the remaining budget is refused by the backend — shown
          here first so it is not a surprise.
        */}
        <div className="rounded-lg border border-border bg-muted/40 p-3">
          <div className="flex items-center justify-between gap-3">
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              <Gauge className="size-3.5" aria-hidden="true" />
              Email service daily budget
            </p>
            {quota?.reachable && quotaUsed !== null && quotaLimit !== null ? (
              <span className="text-xs font-medium tabular">
                {quotaUsed} / {quotaLimit}
              </span>
            ) : (
              <Badge variant="muted">Unknown</Badge>
            )}
          </div>

          {quota?.reachable && quotaUsed !== null && quotaLimit !== null ? (
            <>
              <Progress value={quotaPct} label="Daily email budget used" className="mt-2" />
              <p className="mt-1.5 text-xs text-muted-foreground">
                {quotaLimit - quotaUsed} send{quotaLimit - quotaUsed === 1 ? '' : 's'} left today.
                The budget resets at midnight UTC.
              </p>
            </>
          ) : (
            <p className="mt-1.5 text-xs text-muted-foreground">
              Could not read the email service quota. Preview the audience before sending, and
              keep it small until you have confirmed the real limit.
            </p>
          )}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="mass-subject">Subject</Label>
          <Input
            id="mass-subject"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            placeholder="What's new this week"
            maxLength={200}
            disabled={busy}
          />
          <p className="text-xs text-muted-foreground">
            Supports <code className="font-mono">{'{{placeholder}}'}</code> substitution from the
            variables below.
          </p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="mass-body">Body</Label>
          <Textarea
            id="mass-body"
            rows={5}
            value={body}
            onChange={(event) => setBody(event.target.value)}
            placeholder={'Hi there,\n\nHere is what shipped.'}
            maxLength={20_000}
            disabled={busy}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="mass-campaign-id">Campaign ID (optional)</Label>
          <Input
            id="mass-campaign-id"
            value={campaignId}
            onChange={(event) => setCampaignId(event.target.value)}
            placeholder="e.g. 2026-07-launch"
            maxLength={100}
            disabled={busy}
          />
          <p className="text-xs text-muted-foreground">
            Re-sending with the same ID skips recipients already contacted by it, so a failed
            campaign can be retried safely. Leave blank for a one-off send.
          </p>
        </div>

        <fieldset disabled={busy}>
          <legend className="text-sm font-medium">Priority</legend>
          <div className="mt-2 flex gap-2">
            {(['LOW', 'MEDIUM', 'HIGH'] as const).map((level) => (
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
        </fieldset>

        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => void runPreview()} disabled={!ready || busy}>
            {busy ? 'Checking…' : 'Preview audience'}
          </Button>
          <Button
            onClick={() => setConfirmOpen(true)}
            disabled={!ready || busy || !preview || preview.targeted === 0}
          >
            <Send aria-hidden="true" />
            Send campaign
          </Button>
        </div>

        {preview ? (
          <div className="space-y-2 rounded-lg border border-border p-3">
            <p className="text-sm font-medium">Audience</p>
            <dl className="grid grid-cols-3 gap-3 text-sm">
              <div>
                <dt className="text-xs text-muted-foreground">Would reach</dt>
                <dd className="text-lg font-semibold tabular">{preview.targeted}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Opted out</dt>
                <dd className="text-lg font-semibold tabular">{preview.skippedOptedOut}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Scanned</dt>
                <dd className="text-lg font-semibold tabular">{preview.scanned}</dd>
              </div>
            </dl>
            {preview.message ? (
              <p className="text-xs text-muted-foreground">{preview.message}</p>
            ) : null}
            {preview.targeted > (quotaLimit ?? Infinity) ? (
              <p className="flex items-start gap-1.5 text-xs text-warning-foreground dark:text-foreground">
                <AlertTriangle className="mt-0.5 size-3.5 shrink-0 text-warning" aria-hidden="true" />
                Larger than the email service&apos;s daily quota. Raise the limit there before
                sending, or most of this will be rejected.
              </p>
            ) : null}
            {preview.sent > 0 ? (
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Users className="size-3.5" aria-hidden="true" />
                {preview.sent} sent
                {preview.failed > 0 ? `, ${preview.failed} failed and will retry` : ''}
              </p>
            ) : null}
          </div>
        ) : null}
      </CardContent>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Send this campaign to {preview?.targeted ?? 0} recipient(s)?</DialogTitle>
            <DialogDescription>
              This sends real email to real people. Users who opted out of email are excluded.
              {preview && quotaLimit !== null && preview.targeted > quotaLimit ? (
                <span className="mt-2 block text-warning-foreground dark:text-foreground">
                  Heads up: the email service has {quotaLimit} send(s) left today and this campaign
                  targets {preview.targeted}. The excess will be rejected.
                </span>
              ) : null}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={() => void confirmSend()} loading={busy} loadingText="Sending…">
              Send to {preview?.targeted ?? 0} recipient(s)
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

interface MassEmailRequestBody {
  subject: string;
  body: string;
  variables?: Record<string, string>;
  priority?: 'LOW' | 'MEDIUM' | 'HIGH';
  /** Idempotency key: re-sending with the same id is a per-recipient no-op. */
  campaignId?: string;
}

interface MassEmailPreview {
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
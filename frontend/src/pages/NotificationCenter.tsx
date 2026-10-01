import { useCallback, useEffect, useMemo, useState } from 'react';
import { BellOff, Filter, Search } from 'lucide-react';
import { notificationsApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import { useRealtime } from '@/hooks/useRealtime';
import { dayLabel } from '@/lib/format';
import type { NotificationItem } from '@/types/api';
import { PageHeader } from '@/components/layout/primitives';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { toast } from '@/components/ui/toaster';
import { MarkAllReadButton, NotificationRow } from '@/components/notifications/NotificationRow';

const PAGE_SIZE = 20;

/**
 * Notification centre (spec §21). Server-side data, client-side search/filter over the
 * loaded page (the API paginates; the filters operate on what's loaded, which is stated
 * in the UI rather than implied to be a global filter).
 */
export function NotificationCenter() {
  const { connected, refreshUnread } = useRealtime();

  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [search, setSearch] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<{ id: string; title: string } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await notificationsApi.history(page, PAGE_SIZE));
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return items.filter((item) => {
      if (filter === 'unread' && item.read) return false;
      if (!needle) return true;
      const haystack = `${item.subject ?? ''} ${item.body ?? ''} ${item.eventType} ${item.channel}`.toLowerCase();
      return haystack.includes(needle);
    });
  }, [items, filter, search]);

  /** Groups by day so the list reads as a timeline rather than a flat dump. */
  const grouped = useMemo(() => {
    const buckets = new Map<string, NotificationItem[]>();
    for (const item of visible) {
      const label = dayLabel(new Date(item.createdAt));
      const bucket = buckets.get(label);
      if (bucket) bucket.push(item);
      else buckets.set(label, [item]);
    }
    return [...buckets.entries()];
  }, [visible]);

  const mutate = async (id: string, action: 'read' | 'unread') => {
    setBusyId(id);
    try {
      const updated = action === 'read' ? await notificationsApi.markRead(id) : await notificationsApi.markUnread(id);
      setItems((current) => current.map((item) => (item.id === id ? updated : item)));
      await refreshUnread();
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setBusyId(null);
    }
  };

  const markAllRead = async () => {
    try {
      const result = await notificationsApi.markAllRead();
      setItems((current) => current.map((item) => ({ ...item, read: true })));
      await refreshUnread();
      toast.success('Inbox cleared', { description: `${result.markedRead} notification(s) marked read.` });
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      toast.error(normalized.title, { description: normalized.message });
    }
  };

  const remove = async (id: string) => {
    setBusyId(id);
    try {
      await notificationsApi.remove(id);
      setItems((current) => current.filter((item) => item.id !== id));
      await refreshUnread();
      toast.success('Notification deleted');
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      toast.error(normalized.title, { description: normalized.message });
    } finally {
      setBusyId(null);
      setConfirmDelete(null);
    }
  };

  const unreadInPage = items.filter((item) => !item.read).length;

  return (
    <>
      <PageHeader
        eyebrow="Notifications"
        title="Notification centre"
        description="Everything delivered to your account, grouped by day. New items appear live."
        actions={<MarkAllReadButton onClick={() => void markAllRead()} unread={unreadInPage} />}
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

      {/* Toolbar: search + filter, wrapping cleanly on mobile */}
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-xs">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            id="inbox-search"
            type="search"
            placeholder="Search this page…"
            aria-label="Search loaded notifications"
            className="pl-9"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        <Tabs value={filter} onValueChange={(value) => setFilter(value as 'all' | 'unread')}>
          <TabsList aria-label="Filter notifications">
            <TabsTrigger value="all">
              All <span className="ml-1 text-xs text-muted-foreground tabular">{items.length}</span>
            </TabsTrigger>
            <TabsTrigger value="unread">
              Unread <span className="ml-1 text-xs text-muted-foreground tabular">{unreadInPage}</span>
            </TabsTrigger>
          </TabsList>
        </Tabs>

        <Badge variant={connected ? 'success' : 'warning'} className="w-fit">
          {connected ? 'Live' : 'Reconnecting'}
        </Badge>
      </div>

      {loading ? (
        <div className="space-y-3 rounded-xl border border-border bg-card p-4" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="flex gap-3">
              <Skeleton className="mt-1 size-2 rounded-full" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-3.5 w-1/2" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-2.5 w-1/4" />
              </div>
            </div>
          ))}
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<BellOff />}
          title="No notifications on this page"
          description="When events are published for your account they land here within seconds."
        />
      ) : visible.length === 0 ? (
        <EmptyState
          icon={<Filter />}
          title={filter === 'unread' ? 'Nothing unread' : 'No matches'}
          description={
            filter === 'unread'
              ? 'Every notification on this page has been read.'
              : 'Try a different search term, or clear the filter to see everything on this page.'
          }
          action={{ label: 'Clear filters', onClick: () => { setSearch(''); setFilter('all'); } }}
        />
      ) : (
        <div className="space-y-6">
          {grouped.map(([label, group]) => (
            <section key={label} aria-label={label}>
              <h2 className="mb-2 flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {label}
                <span className="tabular">({group.length})</span>
              </h2>
              <div className="overflow-hidden rounded-xl border border-border bg-card">
                {group.map((item) => (
                  <NotificationRow
                    key={item.id}
                    item={item}
                    onMarkRead={(id) => void mutate(id, 'read')}
                    onMarkUnread={(id) => void mutate(id, 'unread')}
                    onDelete={(id) =>
                      setConfirmDelete({
                        id,
                        title: item.subject?.trim() || item.eventType.replaceAll('_', ' ').toLowerCase(),
                      })
                    }
                    busy={busyId === item.id}
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {/* Pagination — the API is page/size based, so this is a real cursor, not a fake control */}
      <nav aria-label="Pagination" className="mt-6 flex items-center justify-between gap-3">
        <p className="text-xs text-muted-foreground tabular">
          Page {page + 1} · {items.length} loaded
        </p>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={page === 0 || loading} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={items.length < PAGE_SIZE || loading}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      </nav>

      {/* Destructive confirmation — never deletes on a single stray click */}
      <Dialog open={Boolean(confirmDelete)} onOpenChange={(open) => !open && setConfirmDelete(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Delete this notification?</DialogTitle>
            <DialogDescription>
              “{confirmDelete?.title}” will be removed from your inbox. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              loading={busyId === confirmDelete?.id}
              onClick={() => confirmDelete && void remove(confirmDelete.id)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
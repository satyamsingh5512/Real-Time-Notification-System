import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { useRealtime } from '@/stores/realtime';
import { notificationsApi } from '@/services/api';
import { normalizeError, type NormalizedError } from '@/services/errors';
import type { NotificationItem } from '@/types/api';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { ErrorState } from '@/components/ui/error-state';
import { EmptyState } from '@/components/ui/empty-state';
import { NotificationRow } from './NotificationRow';

/**
 * Mobile-friendly notification drawer (spec §21). Slides in from the right on desktop
 * and from the bottom on mobile, with its own scroll container so it never pushes the
 * page. Radix traps focus and closes on Escape.
 */
export function NotificationDrawer({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const navigate = useNavigate();
  const { unread, connected } = useRealtime();
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await notificationsApi.history(0, 12));
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const markRead = async (id: string) => {
    setBusyId(id);
    try {
      const updated = await notificationsApi.markRead(id);
      setItems((current) => current.map((item) => (item.id === id ? updated : item)));
    } catch (caught) {
      setError(normalizeError(caught));
    } finally {
      setBusyId(null);
    }
  };

  const markAllRead = async () => {
    try {
      await notificationsApi.markAllRead();
      setItems((current) => current.map((item) => ({ ...item, read: true })));
    } catch (caught) {
      setError(normalizeError(caught));
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        hideClose
        className="inset-x-0 bottom-0 top-auto max-h-[85dvh] w-full max-w-none translate-x-0 translate-y-0 gap-0 overflow-hidden rounded-t-xl p-0 sm:inset-y-0 sm:left-auto sm:right-0 sm:top-0 sm:h-full sm:max-h-none sm:max-w-md sm:translate-y-0 sm:rounded-none sm:border-l sm:border-t-0"
      >
        <DialogHeader className="shrink-0 border-b border-border p-4">
          <div className="flex items-center justify-between gap-2">
            <div>
              <DialogTitle className="flex items-center gap-2">
                <Bell className="size-4" aria-hidden="true" />
                Notifications
                {unread > 0 ? (
                  <span className="rounded-full bg-primary px-1.5 py-0.5 text-[10px] font-semibold text-primary-foreground tabular">
                    {unread > 99 ? '99+' : unread}
                  </span>
                ) : null}
              </DialogTitle>
              <DialogDescription>
                {connected ? 'Live — new notifications appear automatically.' : 'Reconnecting to live updates…'}
              </DialogDescription>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void markAllRead()}
              disabled={unread === 0}
              aria-label="Mark all notifications as read"
            >
              <CheckCheck aria-hidden="true" />
              <span className="hidden sm:inline">Mark all</span>
            </Button>
          </div>
        </DialogHeader>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {loading ? (
            <div className="space-y-3 p-4" aria-hidden="true">
              {Array.from({ length: 5 }).map((_, index) => (
                <div key={index} className="flex gap-3">
                  <Skeleton className="mt-1 size-2 rounded-full" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-3.5 w-2/3" />
                    <Skeleton className="h-3 w-full" />
                    <Skeleton className="h-2.5 w-1/3" />
                  </div>
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="p-4">
              <ErrorState kind={error.kind} message={error.message} onRetry={() => void load()} compact />
            </div>
          ) : items.length === 0 ? (
            <div className="p-4">
              <EmptyState
                icon={<Bell />}
                title="No notifications yet"
                description="Once events arrive for your account they'll appear here in real time."
                compact
              />
            </div>
          ) : (
            <div>
              {items.map((item) => (
                <NotificationRow
                  key={item.id}
                  item={item}
                  onMarkRead={(id) => void markRead(id)}
                  busy={busyId === item.id}
                />
              ))}
            </div>
          )}
        </div>

        <div className="shrink-0 border-t border-border p-3">
          <Button
            variant="outline"
            className="w-full"
            onClick={() => {
              onOpenChange(false);
              navigate('/inbox');
            }}
          >
            Open notification centre
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
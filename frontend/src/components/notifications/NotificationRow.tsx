import { BellDot, CheckCheck, Trash2 } from 'lucide-react';
import { formatDistanceToNow } from '@/lib/format';
import type { NotificationItem } from '@/types/api';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';

const CHANNEL_TONE: Record<string, 'default' | 'info' | 'success' | 'warning' | 'muted'> = {
  EMAIL: 'default',
  SMS: 'warning',
  PUSH: 'info',
  IN_APP: 'muted',
  WEBSOCKET: 'muted',
};

const STATUS_LABEL: Record<string, string> = {
  SENT: 'Delivered',
  DELIVERED: 'Delivered',
  PENDING: 'Pending',
  SCHEDULED: 'Scheduled',
  PROCESSING: 'Sending',
  RETRYING: 'Retrying',
  FAILED: 'Failed',
  DEAD_LETTERED: 'Failed permanently',
  CANCELLED: 'Cancelled',
  QUEUED_DIGEST: 'In digest',
};

/** Compact relative timestamp ("4m ago") with an absolute title for precision. */
export function RelativeTime({ iso }: { iso: string }) {
  const label = formatDistanceToNow(new Date(iso), { addSuffix: true });
  return (
    <time dateTime={iso} title={new Date(iso).toLocaleString()} className="text-xs text-muted-foreground">
      {label}
    </time>
  );
}

/**
 * Notification row (spec §21). Read state is conveyed by weight + an explicit
 * "New" badge, never by color alone.
 */
export function NotificationRow({
  item,
  onMarkRead,
  onMarkUnread,
  onDelete,
  busy,
}: {
  item: NotificationItem;
  onMarkRead?: (id: string) => void;
  onMarkUnread?: (id: string) => void;
  onDelete?: (id: string) => void;
  busy?: boolean;
}) {
  const title = item.subject?.trim() || item.eventType.replaceAll('_', ' ').toLowerCase();

  return (
    <article
      className={cn(
        'group flex gap-3 border-b border-border px-4 py-3.5 transition-colors last:border-0 hover:bg-muted/40',
        !item.read && 'bg-primary/[0.035]',
      )}
      aria-label={`${title}, ${item.read ? 'read' : 'unread'}`}
    >
      <span
        aria-hidden="true"
        className={cn(
          'mt-2 size-2 shrink-0 rounded-full transition-colors',
          item.read ? 'bg-transparent' : 'bg-primary',
        )}
      />

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className={cn('truncate text-sm', item.read ? 'font-medium' : 'font-semibold')}>{title}</h3>
          {!item.read ? (
            <Badge variant="primary" className="px-1.5 py-0 text-[10px] uppercase">
              New
            </Badge>
          ) : null}
        </div>
        {item.body ? <p className="mt-1 line-clamp-2 text-sm text-pretty text-muted-foreground">{item.body}</p> : null}
        <div className="mt-2 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs">
          <Badge variant={CHANNEL_TONE[item.channel] ?? 'muted'}>{item.channel}</Badge>
          <span className="text-muted-foreground">
            {STATUS_LABEL[item.status] ?? item.status}
          </span>
          <span aria-hidden="true" className="text-border">
            ·
          </span>
          <RelativeTime iso={item.createdAt} />
        </div>
      </div>

      {onMarkRead || onMarkUnread || onDelete ? (
        <div className="flex shrink-0 items-start gap-0.5 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100 max-sm:opacity-100">
          {item.read && onMarkUnread ? (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`Mark "${title}" as unread`}
              disabled={busy}
              onClick={() => onMarkUnread(item.id)}
            >
              <BellDot aria-hidden="true" />
            </Button>
          ) : null}
          {!item.read && onMarkRead ? (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`Mark "${title}" as read`}
              disabled={busy}
              onClick={() => onMarkRead(item.id)}
            >
              <CheckCheck aria-hidden="true" />
            </Button>
          ) : null}
          {onDelete ? (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`Delete "${title}"`}
              disabled={busy}
              className="text-muted-foreground hover:text-destructive"
              onClick={() => onDelete(item.id)}
            >
              <Trash2 aria-hidden="true" />
            </Button>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}


/** "Mark all read" affordance used in both the page header and the drawer. */
export function MarkAllReadButton({
  onClick,
  disabled,
  unread,
}: {
  onClick: () => void;
  disabled?: boolean;
  unread: number;
}) {
  return (
    <Button variant="outline" size="sm" onClick={onClick} disabled={disabled || unread === 0}>
      <CheckCheck aria-hidden="true" />
      Mark all read
    </Button>
  );
}
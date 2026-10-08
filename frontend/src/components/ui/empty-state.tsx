import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/**
 * Empty state (spec §14). Every collection in the app renders one of these instead of
 * a bare "No data" string: it explains *why* the area is empty and offers the next
 * action when one exists.
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
  secondaryAction,
  compact = false,
  className,
}: {
  icon?: ReactNode;
  title: string;
  description: string;
  action?: { label: string; onClick: () => void; loading?: boolean };
  secondaryAction?: { label: string; onClick: () => void };
  compact?: boolean;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-card/40 px-6 text-center',
        compact ? 'py-8' : 'py-14',
        className,
      )}
    >
      {icon ? (
        <span
          className="flex size-11 items-center justify-center rounded-xl border border-border bg-muted/50 text-muted-foreground [&_svg]:size-5"
          aria-hidden="true"
        >
          {icon}
        </span>
      ) : null}
      <div className="space-y-1.5">
        <h3 className="text-base font-semibold tracking-tight">{title}</h3>
        <p className="mx-auto max-w-md text-pretty text-sm text-muted-foreground">{description}</p>
      </div>
      {action || secondaryAction ? (
        <div className="mt-2 flex flex-wrap items-center justify-center gap-2">
          {action ? (
            <button
              type="button"
              onClick={action.onClick}
              disabled={action.loading}
              className="inline-flex h-9 items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50"
            >
              {action.label}
            </button>
          ) : null}
          {secondaryAction ? (
            <button
              type="button"
              onClick={secondaryAction.onClick}
              className="inline-flex h-9 items-center justify-center rounded-md border border-input bg-background px-4 text-sm font-medium shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {secondaryAction.label}
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
import { AlertTriangle, Info, ShieldAlert, XCircle } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { Button } from './button';

type ErrorKind = 'api' | 'auth' | 'forbidden' | 'notFound' | 'validation' | 'rateLimit' | 'server' | 'network' | 'unknown';

const CONFIG: Record<
  ErrorKind,
  { title: string; icon: ReactNode; variant: string; retryable: boolean }
> = {
  api: {
    title: 'Something went wrong on our side',
    icon: <AlertTriangle className="size-4" aria-hidden="true" />,
    variant: 'border-destructive/30 bg-destructive/8 text-destructive',
    retryable: true,
  },
  auth: {
    title: 'Your session has expired',
    icon: <ShieldAlert className="size-4" aria-hidden="true" />,
    variant: 'border-warning/40 bg-warning/10 text-warning-foreground dark:text-warning',
    retryable: false,
  },
  forbidden: {
    title: 'You do not have access to this',
    icon: <ShieldAlert className="size-4" aria-hidden="true" />,
    variant: 'border-warning/40 bg-warning/10 text-warning-foreground dark:text-warning',
    retryable: false,
  },
  notFound: {
    title: 'We could not find that',
    icon: <Info className="size-4" aria-hidden="true" />,
    variant: 'border-border bg-muted text-foreground',
    retryable: false,
  },
  validation: {
    title: 'Please check the details you entered',
    icon: <AlertTriangle className="size-4" aria-hidden="true" />,
    variant: 'border-destructive/30 bg-destructive/8 text-destructive',
    retryable: false,
  },
  rateLimit: {
    title: 'Too many requests',
    icon: <AlertTriangle className="size-4" aria-hidden="true" />,
    variant: 'border-warning/40 bg-warning/10 text-warning-foreground dark:text-warning',
    retryable: true,
  },
  server: {
    title: 'The service is temporarily unavailable',
    icon: <XCircle className="size-4" aria-hidden="true" />,
    variant: 'border-destructive/30 bg-destructive/8 text-destructive',
    retryable: true,
  },
  network: {
    title: 'We could not reach the server',
    icon: <XCircle className="size-4" aria-hidden="true" />,
    variant: 'border-destructive/30 bg-destructive/8 text-destructive',
    retryable: true,
  },
  unknown: {
    title: 'Unexpected error',
    icon: <XCircle className="size-4" aria-hidden="true" />,
    variant: 'border-destructive/30 bg-destructive/8 text-destructive',
    retryable: true,
  },
};

/**
 * Unified error UI (spec §15). Maps an HTTP status to a human-readable recovery path.
 * Never renders stack traces or raw server internals — only the safe `message` the API
 * layer already sanitized.
 */
export function ErrorState({
  kind = 'unknown',
  message,
  onRetry,
  retryLabel = 'Retry',
  action,
  compact = false,
  className,
}: {
  kind?: ErrorKind;
  message?: string;
  onRetry?: () => void;
  retryLabel?: string;
  action?: { label: string; onClick: () => void };
  compact?: boolean;
  className?: string;
}) {
  const config = CONFIG[kind];
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-start gap-3 rounded-xl border p-4',
        config.variant,
        compact ? 'text-sm' : 'sm:p-5',
        className,
      )}
    >
      <div className="flex items-start gap-2.5">
        <span className="mt-0.5 shrink-0" aria-hidden="true">
          {config.icon}
        </span>
        <div className="space-y-1">
          <p className={cn('font-semibold', compact ? 'text-sm' : 'text-base')}>{config.title}</p>
          {message ? <p className="text-sm opacity-90">{message}</p> : null}
        </div>
      </div>
      {onRetry && config.retryable ? (
        <Button size="sm" variant="outline" onClick={onRetry}>
          {retryLabel}
        </Button>
      ) : null}
      {action ? (
        <Button size="sm" variant="outline" onClick={action.onClick}>
          {action.label}
        </Button>
      ) : null}
    </div>
  );
}

export type { ErrorKind };
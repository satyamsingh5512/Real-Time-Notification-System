import { cn } from '@/lib/utils';

/**
 * Skeleton primitives (spec §12). Shapes mirror the real content so the first paint
 * has the same geometry as the loaded state — this is what prevents layout shift.
 */
function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        'animate-shimmer rounded-md bg-[linear-gradient(90deg,var(--color-muted)_25%,color-mix(in_oklab,var(--color-muted)_55%,var(--color-card))_50%,var(--color-muted)_75%)] bg-[length:200%_100%]',
        className,
      )}
      {...props}
    />
  );
}

/** Generic line block, sized by the caller. */
export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('space-y-2', className)} aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <Skeleton key={index} className={cn('h-3.5', index === lines - 1 ? 'w-3/5' : 'w-full')} />
      ))}
    </div>
  );
}



/** Stat-card skeleton matching the dashboard metric layout. */
export function SkeletonStat() {
  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <Skeleton className="mb-2 h-3 w-20" />
      <Skeleton className="h-7 w-14" />
    </div>
  );
}

/**
 * Accessible loading wrapper: announces busy state to screen readers while showing a
 * skeleton of the correct shape.
 */

export { Skeleton };
import { Skeleton, SkeletonStat, SkeletonText } from './skeleton';

/**
 * Page-level skeletons (spec §12).
 *
 * These are composed page shapes, not generic spinners: every one mirrors the real
 * layout's geometry so the first paint and the loaded state occupy the same space and
 * the page does not jump when data lands.
 *
 * Scope note: the specification also lists Project, Team, Member, Files, Billing and
 * AuditLog skeletons. None of those features exist in this backend — there is no
 * project, team, member, upload, plan or audit endpoint — so shipping skeletons for
 * them would be dead UI. See docs/UI-UX.md § "Deliberate omissions".
 */

function HeaderSkeleton() {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0 space-y-2">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-3.5 w-80 max-w-full" />
      </div>
      <div className="flex gap-2">
        <Skeleton className="h-9 w-32" />
        <Skeleton className="h-9 w-28" />
      </div>
    </div>
  );
}

function PanelSkeleton({ className }: { className?: string }) {
  return (
    <div className={`rounded-xl border border-border bg-card p-5 ${className ?? ''}`} aria-hidden="true">
      <Skeleton className="mb-4 h-4 w-40" />
      <SkeletonText lines={3} />
    </div>
  );
}

/** Generic full-page fallback — used while a lazy route chunk loads. */
export function PageSkeleton() {
  return (
    <div className="space-y-6">
      <HeaderSkeleton />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
      <Skeleton className="h-72 w-full rounded-xl" />
    </div>
  );
}

/** Dashboard: four metrics, a two-column body, a session panel. */
export function DashboardSkeleton() {
  return (
    <SkeletonRegion label="Loading your workspace overview">
      <HeaderSkeleton />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
      <div className="mt-6 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <div className="rounded-xl border border-border bg-card">
          <div className="flex items-center justify-between gap-3 p-5">
            <div className="space-y-2">
              <Skeleton className="h-4 w-44" />
              <Skeleton className="h-3 w-56" />
            </div>
            <Skeleton className="h-8 w-20" />
          </div>
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
        </div>
        <div className="space-y-4">
          <PanelSkeleton />
          <PanelSkeleton />
        </div>
      </div>
    </SkeletonRegion>
  );
}

/** One inbox/drawer row — matches NotificationRow's three-column geometry. */
export function NotificationRowSkeleton() {
  return (
    <div className="flex items-start gap-3 border-b border-border px-4 py-3.5 last:border-0">
      <Skeleton className="mt-1.5 size-2 shrink-0 rounded-full" />
      <div className="min-w-0 flex-1 space-y-2">
        <div className="flex items-center gap-2">
          <Skeleton className="h-3.5 w-2/5" />
          <Skeleton className="h-4 w-16 rounded-full" />
        </div>
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-24" />
      </div>
      <Skeleton className="size-7 shrink-0 rounded-md" />
    </div>
  );
}

export function NotificationListSkeleton({
  rows = 6,
  label = 'Loading notifications',
}: {
  rows?: number;
  label?: string;
}) {
  return (
    <SkeletonRegion label={label}>
      <div className="overflow-hidden rounded-xl border border-border bg-card">
        {Array.from({ length: rows }).map((_, index) => (
          <NotificationRowSkeleton key={index} />
        ))}
      </div>
    </SkeletonRegion>
  );
}

/** Preferences: one card per event type, each with controls. */
export function PreferencesSkeleton({ cards = 4 }: { cards?: number }) {
  return (
    <SkeletonRegion label="Loading your delivery preferences">
      <HeaderSkeleton />
      <div className="space-y-4">
        {Array.from({ length: cards }).map((_, index) => (
          <div key={index} className="rounded-xl border border-border bg-card">
            <div className="flex items-center justify-between gap-3 border-b border-border p-5">
              <div className="space-y-2">
                <Skeleton className="h-4 w-36" />
                <Skeleton className="h-3 w-52" />
              </div>
              <Skeleton className="h-6 w-28 rounded-full" />
            </div>
            <div className="space-y-4 p-5">
              <div className="flex flex-wrap gap-2">
                {Array.from({ length: 5 }).map((__, chipIndex) => (
                  <Skeleton key={chipIndex} className="h-8 w-24 rounded-full" />
                ))}
              </div>
              <div className="grid gap-4 sm:grid-cols-3">
                {Array.from({ length: 3 }).map((__, fieldIndex) => (
                  <div key={fieldIndex} className="space-y-2">
                    <Skeleton className="h-3 w-20" />
                    <Skeleton className="h-9 w-full rounded-md" />
                  </div>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </SkeletonRegion>
  );
}

/** Admin console: metric strip + broadcast form + template history table. */
export function AdminSkeleton() {
  return (
    <SkeletonRegion label="Loading the admin console">
      <HeaderSkeleton />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <PanelSkeleton />
        <PanelSkeleton />
      </div>
      <div className="mt-4 overflow-hidden rounded-xl border border-border bg-card">
        <div className="flex gap-4 border-b border-border bg-muted/40 px-4 py-3">
          {['w-24', 'w-16', 'w-20', 'w-12', 'w-14'].map((width, index) => (
            <Skeleton key={index} className={`h-3 ${width}`} />
          ))}
        </div>
        {Array.from({ length: 3 }).map((_, rowIndex) => (
          <div key={rowIndex} className="flex items-center gap-4 border-b border-border px-4 py-4 last:border-0">
            {['w-24', 'w-16', 'w-20', 'w-12', 'w-14'].map((width, index) => (
              <Skeleton key={index} className={`h-3.5 ${width}`} />
            ))}
          </div>
        ))}
      </div>
    </SkeletonRegion>
  );
}


/** System status: three probe cards plus a details table. */
export function SystemStatusSkeleton() {
  return (
    <SkeletonRegion label="Checking service health">
      <HeaderSkeleton />
      <div className="grid gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
      <div className="mt-6 overflow-hidden rounded-xl border border-border bg-card">
        <div className="space-y-3 p-5">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="flex items-center justify-between gap-4">
              <Skeleton className="h-3.5 w-32" />
              <Skeleton className="h-3.5 w-24" />
            </div>
          ))}
        </div>
      </div>
    </SkeletonRegion>
  );
}

function SkeletonRegion({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div role="status" aria-live="polite" aria-busy="true" aria-label={label}>
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}
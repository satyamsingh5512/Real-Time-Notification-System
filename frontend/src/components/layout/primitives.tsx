import { useCallback, useEffect, useRef, useState } from 'react';
import { animateCount, motionEnabled, MOTION, revealStagger } from '@/animations/motion';
import { gsap } from '@/animations/motion';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { cn } from '@/lib/utils';

/**
 * Dashboard metric card (spec §18).
 * Numbers count up once when the value arrives (never continuously), and the card lifts
 * subtly on hover. Under reduced motion the final value is written directly.
 */
export function MetricCard({
  label,
  value,
  suffix,
  hint,
  icon,
  tone = 'default',
  index = 0,
  className,
}: {
  label: string;
  value: number | null;
  suffix?: string;
  hint?: string;
  icon?: React.ReactNode;
  tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger';
  index?: number;
  className?: string;
}) {
  const valueRef = useRef<HTMLSpanElement>(null);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    if (value === null || !valueRef.current) return;
    animateCount(valueRef.current, value, {
      format: (current) => Math.round(current).toLocaleString(),
      duration: reducedMotion ? 0 : MOTION.normal + 0.2,
    });
  }, [value, reducedMotion]);

  const toneClasses: Record<string, string> = {
    default: 'text-foreground',
    primary: 'text-primary',
    success: 'text-success',
    warning: 'text-warning',
    danger: 'text-destructive',
  };

  const iconToneClasses: Record<string, string> = {
    default: 'bg-muted text-muted-foreground',
    primary: 'bg-primary/12 text-primary',
    success: 'bg-success/12 text-success',
    warning: 'bg-warning/15 text-warning',
    danger: 'bg-destructive/12 text-destructive',
  };

  return (
    <div
      data-metric-index={index}
      className={cn(
        'card-hover rounded-xl border border-border bg-card p-5 shadow-sm',
        className,
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        {icon ? (
          <span
            className={cn('flex size-8 items-center justify-center rounded-lg [&_svg]:size-4', iconToneClasses[tone])}
            aria-hidden="true"
          >
            {icon}
          </span>
        ) : null}
      </div>
      <p className={cn('mt-2 text-2xl font-semibold tracking-tight tabular', toneClasses[tone])}>
        {value === null ? (
          <span className="inline-block h-6 w-12 animate-pulse-subtle rounded bg-muted align-middle" />
        ) : (
          <>
            <span ref={valueRef}>0</span>
            {suffix ? <span className="ml-0.5 text-base font-medium text-muted-foreground">{suffix}</span> : null}
          </>
        )}
      </p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

/**
 * Staggers its direct children into view once (spec §18 "dashboard section reveals").
 * Reduced motion renders children untouched.
 */
export function RevealGroup({
  children,
  className,
  stagger = 0.05,
  as: Tag = 'div',
}: {
  children: React.ReactNode;
  className?: string;
  stagger?: number;
  as?: 'div' | 'section' | 'ul';
}) {
  const ref = useRef<HTMLElement>(null);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    const node = ref.current;
    if (!node || reducedMotion || !motionEnabled()) return;
    const context = gsap.context(() => {
      revealStagger(node.children, { stagger });
    }, node);
    return () => context.revert();
  }, [reducedMotion, stagger]);

  return (
    // @ts-expect-error -- polymorphic element, ref type varies by tag
    <Tag ref={ref} className={className}>
      {children}
    </Tag>
  );
}

/**
 * Page header with optional breadcrumbs and sticky behaviour (spec §37).
 * Sticky headers stay thin so they never eat vertical space on mobile.
 */
export function PageHeader({
  title,
  description,
  breadcrumbs,
  actions,
  eyebrow,
  sticky = false,
}: {
  title: string;
  description?: string;
  breadcrumbs?: { label: string; href?: string }[];
  actions?: React.ReactNode;
  eyebrow?: string;
  sticky?: boolean;
}) {
  return (
    <header
      className={cn(
        'z-20 -mx-4 mb-6 px-4 pb-4 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8',
        sticky && 'sticky top-14 border-b border-border bg-background/85 backdrop-blur-md',
      )}
    >
      {breadcrumbs?.length ? (
        <nav aria-label="Breadcrumb" className="mb-1.5">
          <ol className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
            {breadcrumbs.map((crumb, index) => (
              <li key={`${crumb.label}-${index}`} className="flex items-center gap-1.5">
                {crumb.href ? (
                  <a href={crumb.href} className="transition-colors hover:text-foreground">
                    {crumb.label}
                  </a>
                ) : (
                  <span aria-current="page">{crumb.label}</span>
                )}
                {index < breadcrumbs.length - 1 ? (
                  <span aria-hidden="true" className="text-border">
                    /
                  </span>
                ) : null}
              </li>
            ))}
          </ol>
        </nav>
      ) : null}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          {eyebrow ? (
            <p className="text-xs font-medium uppercase tracking-wide text-primary">{eyebrow}</p>
          ) : null}
          <h1 className="mt-0.5 text-2xl font-semibold tracking-tight text-balance sm:text-[1.75rem]">
            {title}
          </h1>
          {description ? (
            <p className="mt-1.5 max-w-2xl text-pretty text-sm text-muted-foreground">{description}</p>
          ) : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}

/**
 * Inline submit-state button (spec §13). Disables only during the in-flight request so
 * accidental double submission is impossible.
 */
export function useSubmitState() {
  const [submitting, setSubmitting] = useState(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async <T,>(task: () => Promise<T>): Promise<T | null> => {
    if (submitting) return null;
    setSubmitting(true);
    try {
      return await task();
    } finally {
      if (mounted.current) setSubmitting(false);
    }
  }, [submitting]);

  return { submitting, run };
}
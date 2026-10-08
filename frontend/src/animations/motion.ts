import gsap from 'gsap';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

export const EASE_OUT_QUART = 'power3.out';

export function motionEnabled(): boolean {
  return !prefersReducedMotion();
}

/**
 * Motion tokens. Durations stay short and purposeful — anything above ~400ms reads as
 * decoration rather than feedback. Transform/opacity only, so everything stays on the
 * compositor thread (GPU-friendly, no layout thrash).
 */
export const MOTION = {
  instant: 0.09,
  fast: 0.15,
  normal: 0.22,
  slow: 0.38,
  /** Distances stay small: large translations read as jank, not polish. */
  rise: 14,
  fadeRise: 16,
  scale: 0.98,
} as const;

/**
 * GSAP config applied to every tween/timeline. `overwrite: auto` prevents competing
 * tweens from fighting each other when a user re-triggers an interaction mid-flight,
 * which is the usual source of "jittery" UI animation.
 *
 * Installed once as a module-level default so every tween in the app inherits it
 * without each call site remembering to pass it.
 */
export const GSAP_DEFAULTS = {
  duration: MOTION.normal,
  ease: EASE_OUT_QUART,
  overwrite: 'auto' as const,
} as const;

gsap.defaults(GSAP_DEFAULTS);

/** Staggered container reveal used for cards, list rows and nav items. */
export function revealStagger(targets: gsap.TweenTarget, options?: gsap.TweenVars): gsap.core.Tween | null {
  if (!motionEnabled()) {
    gsap.set(targets, { clearProps: 'all' });
    return null;
  }
  return gsap.from(targets, {
    opacity: 0,
    y: MOTION.rise,
    duration: MOTION.normal,
    ease: EASE_OUT_QUART,
    stagger: 0.045,
    ...options,
  });
}

/** Number count-up used by metric cards; runs once when data arrives. */
export function animateCount(
  element: HTMLElement,
  to: number,
  options?: { duration?: number; format?: (value: number) => string },
): void {
  const format = options?.format ?? ((value: number) => String(Math.round(value)));
  if (!motionEnabled()) {
    element.textContent = format(to);
    return;
  }
  const state = { value: 0 };
  gsap.to(state, {
    value: to,
    duration: options?.duration ?? 0.5,
    ease: EASE_OUT_QUART,
    onUpdate: () => {
      element.textContent = format(state.value);
    },
  });
}


export { gsap };

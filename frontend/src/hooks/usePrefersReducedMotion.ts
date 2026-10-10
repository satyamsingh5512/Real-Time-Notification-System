import { useEffect, useState } from 'react';

/**
 * Single source of truth for the reduced-motion preference.
 *
 * Every GSAP helper and the hero shader consult this before running anything
 * non-essential. Accessibility outranks animation: when motion is reduced we keep
 * functional transitions (opacity, focus rings) and drop everything decorative.
 */
const QUERY = '(prefers-reduced-motion: reduce)';

export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState<boolean>(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia(QUERY).matches;
  });

  useEffect(() => {
    if (!window.matchMedia) return;
    const query = window.matchMedia(QUERY);
    const onChange = (event: MediaQueryListEvent) => setReduced(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  return reduced;
}

/** Imperative check for non-React contexts (shader bootstrap, event handlers). */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia(QUERY).matches;
}
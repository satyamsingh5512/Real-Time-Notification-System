import { useEffect, useRef } from 'react';
import { MOTION, motionEnabled } from '@/animations/motion';
import { gsap } from '@/animations/motion';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

/**
 * Auth page transition (spec §5): a short rise + fade on mount. Deliberately minimal —
 * auth screens should feel instant, not choreographed.
 */
export function usePageEnter<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    const node = ref.current;
    if (!node || reducedMotion || !motionEnabled()) return;
    const context = gsap.context(() => {
      gsap.from(node.children, {
        opacity: 0,
        y: MOTION.fadeRise,
        duration: MOTION.normal,
        ease: 'power3.out',
        stagger: 0.04,
      });
    }, node);
    return () => context.revert();
  }, [reducedMotion]);

  return ref;
}

/** Notification arrival: the new row slides in rather than popping (spec §21). */
export function useListInsert<T extends HTMLElement>(itemCount: number) {
  const ref = useRef<T>(null);
  const previousCount = useRef(itemCount);
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    const grew = itemCount > previousCount.current;
    previousCount.current = itemCount;
    if (!grew || reducedMotion || !motionEnabled()) return;

    const first = node.firstElementChild;
    if (!first) return;
    const context = gsap.context(() => {
      gsap.fromTo(
        first,
        { opacity: 0, y: -8 },
        { opacity: 1, y: 0, duration: MOTION.normal, ease: 'power3.out' },
      );
    }, node);
    return () => context.revert();
  }, [itemCount, reducedMotion]);

  return ref;
}

export { gsap, MOTION };
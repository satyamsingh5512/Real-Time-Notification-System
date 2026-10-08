import { useEffect, useRef } from 'react';
import { EASE_OUT_QUART, MOTION, motionEnabled, revealStagger } from './motion';
import { gsap } from './motion';

/**
 * Landing-page entrance timeline (spec §35):
 *   shader fade → eyebrow → headline lines → description → CTAs → trust row → preview
 *
 * Total runtime stays under ~1.1s and nothing is interactive-blocking: only opacity and
 * transform animate, and the shader fades in behind rather than gating the text.
 * Under reduced motion every element is set to its final state immediately.
 */
export function playLandingIntro(root: HTMLElement) {
  if (!motionEnabled()) {
    gsap.set(
      [
        '[data-intro="shader"]',
        '[data-intro="eyebrow"]',
        '[data-intro="headline"]',
        '[data-intro="line"]',
        '[data-intro="description"]',
        '[data-intro="cta"]',
        '[data-intro="trust"]',
        '[data-intro="preview"]',
      ],
      { clearProps: 'all' },
    );
    return;
  }

  const timeline = gsap.timeline({ defaults: { overwrite: 'auto' } });
  const headlineLines = root.querySelectorAll('[data-intro="line"]');

  timeline
    .fromTo(
      '[data-intro="shader"]',
      { opacity: 0 },
      { opacity: 1, duration: MOTION.slow, ease: 'power1.out' },
      0,
    )
    .fromTo(
      '[data-intro="eyebrow"]',
      { opacity: 0, y: MOTION.rise },
      { opacity: 1, y: 0, duration: MOTION.fast, ease: EASE_OUT_QUART },
      0.06,
    )
    .fromTo(
      headlineLines.length ? headlineLines : '[data-intro="headline"]',
      { opacity: 0, y: MOTION.fadeRise },
      {
        opacity: 1,
        y: 0,
        duration: MOTION.normal,
        ease: EASE_OUT_QUART,
        stagger: 0.07,
      },
      0.12,
    )
    .fromTo(
      '[data-intro="description"]',
      { opacity: 0, y: MOTION.rise },
      { opacity: 1, y: 0, duration: MOTION.normal, ease: EASE_OUT_QUART },
      0.28,
    )
    .fromTo(
      '[data-intro="cta"]',
      { opacity: 0, y: MOTION.rise },
      { opacity: 1, y: 0, duration: MOTION.normal, ease: EASE_OUT_QUART },
      0.36,
    )
    .fromTo(
      '[data-intro="trust"]',
      { opacity: 0, y: MOTION.rise },
      { opacity: 1, y: 0, duration: MOTION.fast, ease: EASE_OUT_QUART },
      0.44,
    )
    .fromTo(
      '[data-intro="preview"]',
      { opacity: 0, y: MOTION.fadeRise },
      { opacity: 1, y: 0, duration: MOTION.normal, ease: EASE_OUT_QUART },
      0.5,
    );

  return timeline;
}

export interface UseLandingIntroOptions {
  /** Scroll-triggered reveals for everything below the hero. */
  enableScrollReveals?: boolean;
}

/** Wires the landing entrance + section reveals to a container ref. */
export function useLandingIntro<T extends HTMLElement>(options: UseLandingIntroOptions = {}) {
  const ref = useRef<T>(null);
  const { enableScrollReveals = true } = options;

  useEffect(() => {
    const root = ref.current;
    if (!root) return;

    const context = gsap.context(() => {
      playLandingIntro(root);

      if (!enableScrollReveals || !motionEnabled()) return;

      // Lazy-import ScrollTrigger so it never lands in the initial bundle.
      void import('gsap/ScrollTrigger').then(({ ScrollTrigger }) => {
        gsap.registerPlugin(ScrollTrigger);
        ScrollTrigger.getAll().forEach((trigger) => trigger.kill());
        gsap.utils.toArray<HTMLElement>('[data-reveal]').forEach((section) => {
          gsap.from(section, {
            opacity: 0,
            y: MOTION.fadeRise,
            duration: MOTION.normal,
            ease: EASE_OUT_QUART,
            scrollTrigger: { trigger: section, start: 'top 85%', once: true },
          });
        });
        gsap.utils.toArray<HTMLElement>('[data-reveal-group]').forEach((group) => {
          revealStagger(group.children, {
            scrollTrigger: { trigger: group, start: 'top 85%', once: true },
          });
        });
      });
    }, root);

    return () => {
      void import('gsap/ScrollTrigger').then(({ ScrollTrigger }) => {
        ScrollTrigger.getAll().forEach((trigger) => trigger.kill());
      });
      context.revert();
    };
  }, [enableScrollReveals]);

  return ref;
}
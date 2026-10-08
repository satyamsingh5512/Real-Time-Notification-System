import { useId } from 'react';

/**
 * The Notify brand mark.
 *
 * <p>An "N" monogram with a single cyan node sitting at the top-right terminal — the
 * signal arriving at its destination. The node is the only place cyan appears in the
 * product, which is what keeps the mark recognisable; without it the glyph is a generic
 * letterform.
 *
 * <p>Built as vector geometry rather than a font glyph so it renders identically
 * regardless of installed fonts, and so it stays crisp from the 16px collapsed sidebar
 * to a 64px lockup.
 *
 * <p>Replaces what used to be five inconsistent marks across the app: a Lucide `Palette`
 * in the sidebar, a Lucide `Layers` in the landing header and footer, a purple lightning
 * bolt left over from the Vite scaffold in `public/favicon.svg`, and a blue bell in the
 * inline favicon in `index.html`.
 */
export function BrandMark({ className }: { className?: string }) {
  // Gradient ids are document-global. Several marks render at once (sidebar + landing),
  // so each instance needs its own or the second definition silently wins.
  const uid = useId().replace(/:/g, '');
  const fill = `url(#${uid}-tile)`;
  const sheen = `url(#${uid}-sheen)`;

  return (
    <svg
      viewBox="0 0 32 32"
      className={className}
      role="img"
      aria-label="Notify"
      fill="none"
    >
      <defs>
        <linearGradient id={`${uid}-tile`} x1="4" y1="3" x2="28" y2="30" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6178FB" />
          <stop offset="1" stopColor="#1B3A8F" />
        </linearGradient>
        {/* A soft top-left highlight. Purely optical: it stops the flat tile reading as a
            plain filled square, which is what made the previous mark look cheap. */}
        <radialGradient id={`${uid}-sheen`} cx="0" cy="0" r="1" gradientTransform="translate(10 8) rotate(48) scale(24)">
          <stop stopColor="#fff" stopOpacity="0.32" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
      </defs>

      <rect width="32" height="32" rx="9" fill={fill} />
      <rect width="32" height="32" rx="9" fill={sheen} />

      {/* N — custom geometry, not a text glyph, so no font dependency. */}
      <path
        d="M10.3 23.4V9.2L21.8 23.4V9.2"
        stroke="#fff"
        strokeWidth="3.1"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* The node. Cyan, and the only cyan in the product. */}
      <circle cx="24.5" cy="8.7" r="2.3" fill="#8FE3FF" />
    </svg>
  );
}
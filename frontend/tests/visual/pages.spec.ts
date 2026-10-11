import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { freezeHeroCanvas, seedApp, settle, setTheme, stubWebSocket } from './fixtures';

/**
 * Visual regression suite (spec §29).
 *
 * Covers every page in the navigation, at every viewport in the required matrix, in
 * both themes, plus the empty and error states the specification calls for.
 *
 * Determinism: the whole suite runs with `prefers-reduced-motion: reduce`, which the
 * app honours by skipping every GSAP timeline and rendering the hero shader as a
 * single static frame. That removes animation timing from the diff without having to
 * monkey-patch requestAnimationFrame.
 */

const AUTH = { user: undefined as never };

/** Signs in (stubbed) and waits for the shell to be interactive. */
async function signIn(page: Page) {
  await stubWebSocket(page);
  await seedApp(page, AUTH);
}

test.describe('public pages', () => {
  test('landing — light', async ({ page }) => {
    await setTheme(page, 'light');
    await page.goto('/welcome');
    await settle(page);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await freezeHeroCanvas(page);
    await expect(page).toHaveScreenshot('landing-light.png', { fullPage: true });
  });

  test('landing — dark', async ({ page }) => {
    await setTheme(page, 'dark');
    await page.goto('/welcome');
    await settle(page);
    await freezeHeroCanvas(page);
    await expect(page).toHaveScreenshot('landing-dark.png', { fullPage: true });
  });

  test('landing — hero viewport only', async ({ page }) => {
    await setTheme(page, 'light');
    await page.goto('/welcome');
    await settle(page);
    await expect(page).toHaveScreenshot('landing-hero.png', { fullPage: false });
  });

  test('login', async ({ page }) => {
    await setTheme(page, 'light');
    await page.goto('/login');
    await settle(page);
    await expect(page).toHaveScreenshot('login.png', { fullPage: true });
  });

  test('register', async ({ page }) => {
    await setTheme(page, 'light');
    await page.goto('/register');
    await settle(page);
    await expect(page).toHaveScreenshot('register.png', { fullPage: true });
  });
});

test.describe('authenticated pages', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('dashboard', async ({ page }) => {
    await page.goto('/dashboard');
    await settle(page);
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();
    await expect(page).toHaveScreenshot('dashboard.png', { fullPage: true });
  });

  test('notification centre', async ({ page }) => {
    await page.goto('/inbox');
    await settle(page);
    await expect(page.getByRole('heading', { name: /notification centre/i })).toBeVisible();
    await expect(page).toHaveScreenshot('inbox.png', { fullPage: true });
  });

  test('delivery preferences', async ({ page }) => {
    await page.goto('/preferences');
    await settle(page);
    await expect(page.getByRole('heading', { name: /notifications & delivery/i })).toBeVisible();
    await expect(page).toHaveScreenshot('preferences.png', { fullPage: true });
  });

  test('settings', async ({ page }) => {
    await page.goto('/settings');
    await settle(page);
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
    await expect(page).toHaveScreenshot('settings.png', { fullPage: true });
  });

  test('settings — security panel', async ({ page }) => {
    await page.goto('/settings');
    await settle(page);
    await page.getByRole('button', { name: 'Security' }).click();
    await settle(page);
    await expect(page).toHaveScreenshot('settings-security.png', { fullPage: true });
  });

  test('settings — danger zone', async ({ page }) => {
    await page.goto('/settings');
    await settle(page);
    await page.getByRole('button', { name: 'Danger zone' }).click();
    await settle(page);
    await expect(page).toHaveScreenshot('settings-danger.png', { fullPage: true });
  });

  test('system status', async ({ page }) => {
    await page.goto('/status');
    await settle(page);
    await expect(page.getByRole('heading', { name: 'System status' })).toBeVisible();
    await expect(page).toHaveScreenshot('system-status.png', { fullPage: true });
  });

  test('notification drawer', async ({ page }) => {
    await page.goto('/dashboard');
    await settle(page);
    await page.getByRole('button', { name: /notifications/i }).first().click();
    await settle(page);
    await expect(page).toHaveScreenshot('notification-drawer.png', { fullPage: false });
  });

  test('command palette', async ({ page }) => {
    await page.goto('/dashboard');
    await settle(page);
    // ⌘K / Ctrl+K is the documented shortcut, so the suite uses it rather than a click.
    await page.keyboard.press('ControlOrMeta+k');
    await settle(page);
    await expect(page.getByPlaceholder(/type a command/i)).toBeVisible();
    await expect(page).toHaveScreenshot('command-palette.png', { fullPage: false });
  });

  test('admin console', async ({ page }) => {
    await page.goto('/admin');
    await settle(page);
    await expect(page.getByRole('heading', { name: 'Admin console' })).toBeVisible();
    await expect(page).toHaveScreenshot('admin.png', { fullPage: true });
  });

  test('mass email — audience preview', async ({ page }) => {
    await page.goto('/admin');
    await settle(page);

    // Drive the real flow: type, preview, confirm. The recipient count on the
    // confirmation comes from the preview, so previewing first is what makes it safe.
    await page.getByLabel('Subject').fill('What shipped this week');
    await page.getByLabel('Body').fill('Hi there,\n\nHere is what changed across the platform.');
    await page.getByRole('button', { name: 'Preview audience' }).click();
    // `exact` disambiguates from the summary sentence, which also starts "Would reach".
    await expect(page.getByText('Would reach', { exact: true })).toBeVisible();
    await settle(page);

    // Viewport-only, with the panel scrolled into view. A full-page capture of a page
    // this tall makes Chromium render the `position: fixed` sidebar mid-page while it
    // stitches, which is a capture artifact rather than the layout a user sees.
    await page.getByRole('heading', { name: 'Mass email' }).scrollIntoViewIfNeeded();
    await settle(page);
    await expect(page).toHaveScreenshot('admin-mass-email.png', { fullPage: false });

    // The send action is confirmation-gated and names the recipient count.
    await page.getByRole('button', { name: 'Send campaign' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await settle(page);
    await expect(page).toHaveScreenshot('admin-mass-email-confirm.png', { fullPage: false });
  });

  test('dashboard — dark', async ({ page }) => {
    await setTheme(page, 'dark');
    await page.goto('/dashboard');
    await settle(page);
    await expect(page).toHaveScreenshot('dashboard-dark.png', { fullPage: true });
  });

  test('inbox — dark', async ({ page }) => {
    await setTheme(page, 'dark');
    await page.goto('/inbox');
    await settle(page);
    await expect(page).toHaveScreenshot('inbox-dark.png', { fullPage: true });
  });
});

test.describe('empty states', () => {
  test('inbox — nothing delivered', async ({ page }) => {
    await stubWebSocket(page);
    await seedApp(page, { mode: 'empty' });
    await page.goto('/inbox');
    await settle(page);
    await expect(page.getByText(/no notifications on this page/i)).toBeVisible();
    await expect(page).toHaveScreenshot('inbox-empty.png', { fullPage: true });
  });

  test('dashboard — nothing delivered', async ({ page }) => {
    await stubWebSocket(page);
    await seedApp(page, { mode: 'empty' });
    await page.goto('/dashboard');
    await settle(page);
    await expect(page.getByText(/nothing delivered yet/i)).toBeVisible();
    await expect(page).toHaveScreenshot('dashboard-empty.png', { fullPage: true });
  });
});

test.describe('error states', () => {
  test('dashboard — API unavailable', async ({ page }) => {
    await stubWebSocket(page);
    await seedApp(page, { mode: 'error' });
    await page.goto('/dashboard');
    await settle(page);
    await expect(page).toHaveScreenshot('dashboard-error.png', { fullPage: true });
  });

  test('inbox — API unavailable', async ({ page }) => {
    await stubWebSocket(page);
    await seedApp(page, { mode: 'error' });
    await page.goto('/inbox');
    await settle(page);
    await expect(page).toHaveScreenshot('inbox-error.png', { fullPage: true });
  });
});

/**
 * Layout invariants (spec §28). These are assertions rather than screenshots, so they
 * fail loudly with a readable message instead of as an opaque pixel diff.
 */
test.describe('responsive invariants', () => {
  for (const path of ['/welcome', '/login']) {
    test(`${path} — no horizontal overflow`, async ({ page }) => {
      await page.goto(path);
      await settle(page);

      const overflow = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      expect(
        overflow.scrollWidth,
        `${path} overflows horizontally by ${overflow.scrollWidth - overflow.clientWidth}px at ${page.viewportSize()?.width}px`,
      ).toBeLessThanOrEqual(overflow.clientWidth + 1);
    });
  }

  test('authenticated shell — no horizontal overflow', async ({ page }) => {
    await signIn(page);
    await page.goto('/inbox');
    await settle(page);

    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    expect(
      overflow.scrollWidth,
      `Inbox overflows horizontally by ${overflow.scrollWidth - overflow.clientWidth}px`,
    ).toBeLessThanOrEqual(overflow.clientWidth + 1);
  });

  test('mobile navigation opens and traps focus', async ({ page }) => {
    const isDesktop = (page.viewportSize()?.width ?? 0) >= 1024;
    test.skip(isDesktop, 'The slide-over navigation only exists below the lg breakpoint.');

    await signIn(page);
    await page.goto('/dashboard');
    await settle(page);

    await page.getByRole('button', { name: 'Open navigation' }).click();
    const dialog = page.getByRole('dialog', { name: 'Navigation' });
    await expect(dialog).toBeVisible();

    // Focus must have moved into the panel, not stayed behind the backdrop.
    const insideDialog = await page.evaluate(() => {
      const panel = document.querySelector('[role="dialog"][aria-label="Navigation"]');
      return Boolean(panel && document.activeElement && panel.contains(document.activeElement));
    });
    expect(insideDialog, 'Focus did not move into the mobile navigation').toBe(true);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
  });

  test('command palette opens with the keyboard', async ({ page }) => {
    await signIn(page);
    await page.goto('/dashboard');
    await settle(page);

    await page.keyboard.press('ControlOrMeta+k');
    await expect(page.getByPlaceholder(/type a command/i)).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByPlaceholder(/type a command/i)).toBeHidden();
  });

  test('every page renders without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(message.text());
    });
    page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));

    await signIn(page);
    for (const path of ['/dashboard', '/inbox', '/preferences', '/settings', '/status', '/admin']) {
      await page.goto(path);
      await settle(page);
    }

    // Filter out expected noise from stubbed endpoints so the assertion stays useful.
    const real = errors.filter(
      (message) => !/favicon|net::ERR|Failed to load resource/i.test(message),
    );
    expect(real, `Console errors:\n${real.join('\n')}`).toEqual([]);
  });
});
/**
 * Text contrast (spec §6).
 *
 * The hero previously used theme text colours over a shader that always renders a dark
 * navy field. In light mode that put dark ink on near-black — a screenshot caught it,
 * but a screenshot diff only tells you "something changed", not why. This measures
 * effective contrast of every visible hero text node and fails with the offending
 * selector.
 *
 * The background is sampled by reading actual pixels from a screenshot of the region
 * *behind* the text, because a WebGL canvas is invisible to `getComputedStyle`: the
 * shader paints into a canvas with no CSS background colour to inspect. Sampling the
 * rendered frame is the only honest way to know what the copy actually sits on.
 *
 * Threshold is 4.5:1 (WCAG AA normal text), 3:1 for large text (>=24px, or >=18.66px
 * bold), matching the guideline.
 */
test.describe('contrast', () => {
  for (const theme of ['light', 'dark'] as const) {
    test(`landing hero — ${theme} theme copy is legible over the shader`, async ({ page }) => {
      await setTheme(page, theme);
      await page.goto('/welcome');
      await settle(page);

      // Measure the text colours and their rectangles.
      const nodes = await page.evaluate(() => {
        const hero = document.querySelector('section');
        if (!hero) return [];
        return Array.from(hero.querySelectorAll<HTMLElement>('h1, h2, h3, p, li, a, span'))
          .filter((element) => {
            const text = element.textContent?.trim();
            if (!text || element.children.length > 2) return false;
            const rect = element.getBoundingClientRect();
            if (rect.width === 0 || rect.height === 0) return false;
            const style = getComputedStyle(element);
            return style.visibility !== 'hidden' && style.opacity !== '0';
          })
          .map((element) => {
            const style = getComputedStyle(element);
            /*
             * Measure a tight box around the glyphs via a Range, not the element box.
             * The element box of the hero eyebrow also contains a decorative light dot,
             * and the worst-case pixel inside it was the dot — not the surface the text
             * actually sits on — which produced a false failure.
             */
            const range = document.createRange();
            /*
             * Range the element's own TEXT nodes only — never `selectNodeContents`,
             * which also spans element children. The hero eyebrow contains a decorative
             * light-green dot; including it put a near-white pixel in the sample region
             * and produced a false 1.18:1 failure on copy that actually measures 11:1.
             */
            let sawText = false;
            let lastText: ChildNode | null = null;
            for (const child of Array.from(element.childNodes)) {
              if (child.nodeType !== Node.TEXT_NODE || !child.textContent?.trim()) continue;
              if (sawText) {
                range.setStartAfter(lastText!);
              } else {
                range.setStart(child, 0);
                sawText = true;
              }
              lastText = child;
              range.setEnd(child, child.textContent!.length);
            }
            const boxRange = sawText ? range.getBoundingClientRect() : null;
            const rect =
              boxRange && boxRange.width > 0 && boxRange.height > 0
                ? boxRange
                : element.getBoundingClientRect();
            return {
              label: `${element.tagName.toLowerCase()}: "${element.textContent?.trim().slice(0, 32)}"`,
              color: style.color,
              fontSize: Number.parseFloat(style.fontSize),
              weight: Number.parseInt(style.fontWeight, 10) || 400,
              box: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
            };
          });
      });

      expect(nodes.length, 'No hero text nodes were found to measure').toBeGreaterThan(0);

      // Sample what is painted behind the text by making the text transparent while
      // leaving every surface in place.
      //
      // `visibility: hidden` was the first attempt and it is wrong: for a control
      // rendered `asChild` (Button -> Link) the element *is* the painted surface, so
      // hiding it deleted its own background and the measurement compared white text
      // against the page behind it. Transparent text keeps the background intact.
      await page.addStyleTag({
        content: `section h1, section h2, section h3, section p, section li, section a, section span,
                   section strong, section em {
                     color: transparent !important;
                     -webkit-text-fill-color: transparent !important;
                     text-shadow: none !important;
                   }`,
      });
      // Wait for the repaint. Without this the screenshot still contains the very text
      // being measured, and every ratio collapses toward 1:1.
      await page.evaluate(
        () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
      );
      const background = await page.screenshot({ fullPage: false });

      // One decode for every node: decoding the PNG per node made this take minutes.
      const failures = await page.evaluate(
        async ({ shot, targets }) => {
          /*
           * Normalise any CSS colour syntax to sRGB by letting the browser do the
           * conversion. A regex over the string is not enough: the design tokens are
           * declared in `oklch()`, and naively reading "[0.97, 0.004, 250]" as RGB
           * made every text colour look black and collapsed all contrast ratios
           * toward 1:1. Painting into a canvas returns real 0-255 channels.
           */
          const probe = document.createElement('canvas');
          probe.width = 1;
          probe.height = 1;
          const probeContext = probe.getContext('2d', { willReadFrequently: true });
          const parse = (value: string): [number, number, number] => {
            if (!probeContext) return [0, 0, 0];
            probeContext.clearRect(0, 0, 1, 1);
            probeContext.fillStyle = '#000';
            probeContext.fillStyle = value;
            probeContext.fillRect(0, 0, 1, 1);
            const [r, g, b] = probeContext.getImageData(0, 0, 1, 1).data;
            return [r, g, b];
          };
          const luminance = ([r, g, b]: [number, number, number]) => {
            const channel = (value: number) => {
              const c = value / 255;
              return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
            };
            return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
          };
          const ratio = (a: number, b: number) => (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);

          const bitmap = await createImageBitmap(
            new Blob([new Uint8Array(shot)], { type: 'image/png' }),
          );
          const canvas = document.createElement('canvas');
          canvas.width = bitmap.width;
          canvas.height = bitmap.height;
          const context = canvas.getContext('2d');
          if (!context) return ['Could not obtain a 2D context to sample the background.'];
          context.drawImage(bitmap, 0, 0);
          const scaleX = bitmap.width / window.innerWidth;
          const scaleY = bitmap.height / window.innerHeight;

          const problems: string[] = [];
          for (const target of targets) {
            const x = Math.max(0, Math.round(target.box.x * scaleX));
            const y = Math.max(0, Math.round(target.box.y * scaleY));
            const width = Math.max(1, Math.round(target.box.width * scaleX));
            const height = Math.max(1, Math.round(target.box.height * scaleY));

            /*
             * Skip anything outside the captured viewport. `getImageData` does NOT
             * throw for out-of-range coordinates — it silently returns transparent
             * black, which measured as "text on #000" and produced false failures for
             * every element below the fold (the whole preview card at 390px).
             */
            if (x + width > bitmap.width || y + height > bitmap.height) continue;
            if (x < 0 || y < 0) continue;

            let region: ImageData;
            try {
              region = context.getImageData(x, y, width, height);
            } catch {
              continue;
            }
            // Fully transparent region: nothing was painted here.
            let opaque = false;
            for (let i = 3; i < region.data.length; i += 4) {
              if (region.data[i] > 8) {
                opaque = true;
                break;
              }
            }
            if (!opaque) continue;

            const textLuminance = luminance(parse(target.color));
            // A gradient behind text means the *worst* sampled pixel is the honest
            // reading, not the average.
            let worst = Number.POSITIVE_INFINITY;
            for (let i = 0; i < region.data.length; i += 4) {
              worst = Math.min(
                worst,
                ratio(textLuminance, luminance([region.data[i], region.data[i + 1], region.data[i + 2]])),
              );
            }

            const required =
              target.fontSize >= 24 || (target.fontSize >= 18.66 && target.weight >= 700) ? 3 : 4.5;
            if (worst < required) {
              const textRgb = parse(target.color);
              const first: [number, number, number] = [
                region.data[0],
                region.data[1],
                region.data[2],
              ];
              // Recompute the worst pixel from scratch. Comparing against the
              // already-minimal `worst` would never update the index, so the report
              // would always point at pixel 0 and tell you nothing.
              let worstValue = Number.POSITIVE_INFINITY;
              let worstIndex = 0;
              for (let i = 0; i < region.data.length; i += 4) {
                const value = ratio(
                  textLuminance,
                  luminance([region.data[i], region.data[i + 1], region.data[i + 2]]),
                );
                if (value < worstValue) {
                  worstValue = value;
                  worstIndex = i / 4;
                }
              }
              const worstPixel: [number, number, number] = [
                region.data[worstIndex * 4],
                region.data[worstIndex * 4 + 1],
                region.data[worstIndex * 4 + 2],
              ];
              problems.push(
                `${target.label} → ${worst.toFixed(2)}:1 (needs ${required}:1) ` +
                  `[text ${target.color} → rgb(${textRgb.join(',')}) L=${textLuminance.toFixed(3)}; ` +
                  `first px rgb(${first.join(',')}) L=${luminance(first).toFixed(3)}; ` +
                  `worst px rgb(${worstPixel.join(',')}) L=${luminance(worstPixel).toFixed(3)} ` +
                  `at +${worstIndex % width},+${Math.floor(worstIndex / width)} of ${width}x${height}]`,
              );
            }
          }
          return problems;
        },
        { shot: [...background], targets: nodes },
      );

      expect(
        failures,
        `Low-contrast hero text in ${theme} mode:\n  ${failures.join('\n  ')}`,
      ).toEqual([]);
    });
  }
});

/**
 * CDN independence and CSP health (spec §31).
 *
 * Asserted against the real production bundle: every request must be same-origin, and
 * the enforcing Content-Security-Policy must not report a violation on any page. A
 * silent CSP breakage would otherwise only surface as a blank element.
 */
test.describe('delivery policy', () => {
  test('no CDN runtime dependency and no CSP violations', async ({ page }) => {
    const external: string[] = [];
    const violations: string[] = [];

    page.on('request', (request) => {
      const url = new URL(request.url());
      if (!['localhost', '127.0.0.1'].includes(url.hostname) && url.protocol !== 'data:') {
        external.push(request.url());
      }
    });
    page.on('console', (message) => {
      const text = message.text();
      if (/Content Security Policy|Refused to/i.test(text)) violations.push(text);
    });
    page.on('pageerror', (error) => violations.push(`pageerror: ${error.message}`));

    for (const path of ['/welcome', '/login', '/register']) {
      await page.goto(path);
      await settle(page);
      // The landing page requests the font; the auth pages exercise inline styles.
      await page.reload();
      await settle(page);
    }

    expect(external, `CDN / cross-origin runtime requests:\n  ${external.join('\n  ')}`).toEqual([]);
    expect(
      violations,
      `Content-Security-Policy violations:\n  ${violations.join('\n  ')}`,
    ).toEqual([]);
  });
});

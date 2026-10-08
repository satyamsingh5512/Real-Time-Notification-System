import { defineConfig, devices } from '@playwright/test';

/**
 * Visual regression config (spec §29).
 *
 * The viewport matrix is the eight breakpoints the specification requires. Each is run
 * as its own project so a failure names the exact size that regressed.
 *
 * Two servers are started:
 *  - the Vite dev server (proxies /api and /ws to the backend, when one is running)
 *  - `vite preview`, serving the real production build
 *
 * Tests target the production build by default because that is what ships; pass
 * `--project=…` with `DEV=1` to exercise the dev server instead.
 */
const PREVIEW_PORT = Number(process.env.PREVIEW_PORT ?? 4173);
const DEV_PORT = Number(process.env.DEV_PORT ?? 5173);
const useDevServer = process.env.DEV === '1';

/** The specification's required viewport matrix (spec §28). */
export const VIEWPORTS = [
  { name: 'mobile-390', width: 390, height: 844, isMobile: true },
  { name: 'mobile-412', width: 412, height: 915, isMobile: true },
  { name: 'tablet-768', width: 768, height: 1024, isMobile: false },
  { name: 'landscape-1024', width: 1024, height: 768, isMobile: false },
  { name: 'laptop-1280', width: 1280, height: 800, isMobile: false },
  { name: 'laptop-1366', width: 1366, height: 768, isMobile: false },
  { name: 'desktop-1440', width: 1440, height: 900, isMobile: false },
  { name: 'desktop-1920', width: 1920, height: 1080, isMobile: false },
] as const;

export default defineConfig({
  testDir: './tests/visual',
  outputDir: './test-results/visual',
  snapshotDir: './tests/visual/__screenshots__',

  // Animations are timing-dependent, so the project-level snapshot settings below
  // rely on the app honouring reduced motion — which it does: with
  // `prefers-reduced-motion: reduce` every GSAP timeline is skipped and the hero
  // shader renders a single static frame. That makes screenshots deterministic
  // instead of requiring animation freezing.
  expect: {
    toHaveScreenshot: {
      animations: 'disabled',
      caret: 'hide',
      scale: 'css',
      maxDiffPixelRatio: 0.02,
      threshold: 0.15,
    },
  },

  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],

  use: {
    baseURL: `http://localhost:${useDevServer ? DEV_PORT : PREVIEW_PORT}`,
    // Reduced motion doubles as the determinism switch for screenshots. It is also
    // the accessibility baseline we want to assert against on every page.
    reducedMotion: 'reduce',
    trace: 'retain-on-failure',
    screenshot: 'off',
  },

  projects: VIEWPORTS.map((viewport) => ({
    name: viewport.name,
    use: {
      ...devices['Desktop Chrome'],
      viewport: { width: viewport.width, height: viewport.height },
      isMobile: viewport.isMobile,
      hasTouch: viewport.isMobile,
      deviceScaleFactor: 1,
    },
  })),

  webServer: useDevServer
    ? {
        command: 'npm run dev',
        url: `http://localhost:${DEV_PORT}`,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      }
    : {
        command: 'npm run build && npm run preview -- --port ' + PREVIEW_PORT,
        url: `http://localhost:${PREVIEW_PORT}`,
        reuseExistingServer: !process.env.CI,
        timeout: 300_000,
      },
});
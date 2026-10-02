# Notify — frontend

React + TypeScript + Vite + Tailwind v4 SPA for the notification platform. Compiles
to static assets served by nginx alongside the Spring Boot API.

Full documentation: [`docs/UI-UX.md`](../docs/UI-UX.md),
[`docs/FRONTEND_ARCHITECTURE.md`](../docs/FRONTEND_ARCHITECTURE.md),
[`docs/VISUAL_REGRESSION.md`](../docs/VISUAL_REGRESSION.md).

---

## Quick start

```bash
cd frontend
npm ci
npm run dev            # http://localhost:5173, proxies /api and /ws to :8080
```

The backend must be running on port 8080 for authenticated pages to have data:

```bash
./gradlew :api:bootRun          # from the repo root
# or
docker compose up -d            # Postgres + Redis + Kafka + backend + nginx on :80
```

If you just want the backend API without any infrastructure, note that
`GET /health` works standalone but the app requires Postgres, Redis and Kafka.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Vite dev server on :5173 |
| `npm run build` | `tsc -b` (typecheck gate) then `vite build` → `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | oxlint |
| `npm run test:visual` | Playwright visual regression against the baselines |
| `npm run test:visual:update` | Regenerate baselines — **review the diff before committing** |
| `npm run test:visual:report` | Open the HTML report |
| `npm run test:visual:install` | One-time: download Chromium |

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_URL` | `''` | API origin. Empty → same-origin `/api/...` through nginx |
| `VITE_WS_URL` | `''` | WebSocket origin. Empty → `ws(s)://<host>/ws/notifications` |

Copy `.env.example` to `.env` for local overrides. In Docker, pass them as build args:

```bash
docker build -f frontend/Dockerfile \
  --build-arg VITE_API_URL=https://api.example.com \
  --target build .
```

Both default to empty, which is the correct production setup: nginx serves the SPA and
reverse-proxies both `/api` and `/ws`, so everything is same-origin and the CSP stays
`'self'`.

## Project layout

```
src/
├── App.tsx            providers + route table
├── animations/        GSAP layer (the only place GSAP is imported)
├── components/
│   ├── ui/            design-system primitives
│   ├── layout/        AppShell, CommandPalette, page primitives
│   ├── landing/       landing page + WebGL hero
│   ├── auth/          login / register
│   └── notifications/ drawer, row
├── hooks/             useHotkey, usePrefersReducedMotion
├── lib/               cn(), formatters
├── pages/             route components
├── services/          api-client (fetch) + api (endpoints)
├── shaders/           GLSL
├── stores/            auth, theme, realtime contexts
├── styles/            globals.css (design tokens), fonts.css
└── types/             wire types mirroring the backend DTOs
```

## Notes for contributors

- **No hardcoded colours.** Use the tokens in `styles/globals.css`. If you need a new
  one, add it to both `:root` and `.dark` and map it in `@theme inline`.
- **One WebSocket.** `RealtimeProvider` owns it. Never call `useRealtime()` from a
  component expecting a new socket.
- **Respect reduced motion.** Every animation entry point checks `motionEnabled()`
  first, and `globals.css` has a CSS safety net. Never gate content behind an
  animation.
- **Do not invent endpoints.** `services/api.ts` maps 1:1 onto real controllers. If a
  feature has no endpoint, it does not go in the UI — see §5 of
  `docs/FRONTEND_ARCHITECTURE.md` for what was deliberately left out and why.
- **Add a skeleton** for any new page's initial load. `components/ui/skeletons.tsx`.

## Docker

`frontend/Dockerfile` is multi-stage: `node:22-alpine` builds, `nginx:1.27-alpine`
serves. **Node is never present in the runtime image.**

The build context must be the **repository root**, because the Dockerfile copies both
`frontend/` and `nginx.conf`:

```bash
docker build -f frontend/Dockerfile -t notify-frontend .
```

In production, `docker-compose.prod.yml` bind-mounts `nginx.prod.conf` over the baked
config to terminate TLS. Set the certificate paths in that file and uncomment the
`/etc/letsencrypt` volume mount before starting — nginx will not start otherwise, by
design.

## Verifying the delivery layer

Security headers, cache policy and the container are owned by nginx, not by Vite — so
they are checked by a separate script rather than by Playwright (which would only be
testing `vite preview`):

```bash
../scripts/verify_delivery.sh                                # throwaway container
BASE_URL=https://your-host ../scripts/verify_delivery.sh     # deployed instance
```

It asserts the CSP is present on **every** SPA route, that assets are immutably cached
while `index.html` is not, that the SPA fallback works, that `/health/ready` really
reaches the backend, and that Node is absent from the runtime image.

## Troubleshooting

**Blank page after `npm run dev`** — check the console for a nested `<Router>` error.
`App.tsx` owns the single `BrowserRouter`; `main.tsx` must not add another.

**WebSocket never connects** — the JWT travels as a `?token=` query parameter because
the browser WebSocket API cannot set headers. Confirm the backend is reachable and
that the token in `localStorage['rtns.token']` is current.

**Fonts look like DejaVu Sans** — Inter is self-hosted from
`@fontsource-variable/inter`. If it is not loading, check that `src/styles/fonts.css`
is imported and that `font-src 'self'` is present in the CSP.

**A visual test fails after an intended redesign** — run
`npm run test:visual:update`, then read the diff. If the new baseline looks wrong, fix
the UI rather than re-baselining over it.
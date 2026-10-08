import { motionEnabled } from '@/animations/motion';
import {
  Activity,
  ArrowRight,
  Bell,
  Braces,
  Database,
  Fingerprint,
  Gauge,
  GitBranch,
  KeyRound,
  Layers,
  Lock,
  Mail,
  MessageSquare,
  Radio,
  RefreshCw,
  ServerCog,
  ShieldCheck,
  Smartphone,
  Webhook,
  Workflow,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { useLandingIntro } from '@/animations/landing';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { BrandMark } from '@/components/brand/BrandMark';
import { ShaderBackground } from './ShaderBackground';
import { cn } from '@/lib/utils';

interface Feature {
  icon: LucideIcon;
  title: string;
  body: string;
  detail?: string;
}

/**
 * Feature content is deliberately factual: every claim maps to code in this repository
 * (spec §10, §15 — no invented customers, metrics or capabilities).
 */
const PIPELINE_FEATURES: Feature[] = [
  {
    icon: Radio,
    title: 'Event ingestion',
    body: 'Eight business event types consumed from Kafka with schema validation before fan-out. Malformed payloads are quarantined with a precise reason instead of half-writing rows.',
  },
  {
    icon: Workflow,
    title: 'Per-event channel routing',
    body: 'A static routing table decides which channels each event type reaches. Opt-out preferences and quiet hours are evaluated per user, per event.',
  },
  {
    icon: Gauge,
    title: 'Intent vs delivery split',
    body: 'Intent (all / mentions / mute) decides whether an event notifies at all. Channel toggles, push switch and quiet hours decide how it reaches you.',
  },
  {
    icon: Layers,
    title: 'Digest batching',
    body: 'Opted-in low-value email is batched per user and flushed as one grouped message. Empty digests are never sent and failures stay queued.',
  },
];

const CONTROL_FEATURES: Feature[] = [
  {
    icon: ShieldCheck,
    title: 'JWT + RBAC',
    body: 'BCrypt-12 password hashing, short-lived signed tokens, and ADMIN / SERVICE / USER roles enforced at both the URL and method level.',
  },
  {
    icon: Fingerprint,
    title: 'WebSocket handshake auth',
    body: 'Realtime connections authenticate during the upgrade handshake rather than trusting the HTTP layer, so an unauthenticated socket never receives a frame.',
  },
  {
    icon: Lock,
    title: 'Fail-open rate limiting',
    body: 'Fixed-window Redis limits on hot paths return 429 with Retry-After. If Redis is unavailable the limiter fails open rather than taking the API down.',
  },
  {
    icon: KeyRound,
    title: 'Secrets enforced at boot',
    body: 'Production refuses to start with a placeholder or short JWT secret, so a misconfigured deploy fails loudly instead of running insecurely.',
  },
];

const RELIABILITY_FEATURES: Feature[] = [
  {
    icon: RefreshCw,
    title: 'Bounded retries and a DLQ',
    body: 'Transient failures back off exponentially into time-bucketed retry topics. Exhausted or permanent failures route to a dead-letter topic with webhook alerting.',
  },
  {
    icon: Activity,
    title: 'Provider circuit breakers',
    body: 'Per-channel breakers open after repeated retryable failures, so a throttling provider fails fast into the retry path instead of burning threads.',
  },
  {
    icon: Database,
    title: 'Versioned schema migrations',
    body: 'Flyway owns the schema and Hibernate only validates it. Migrations run before the app serves traffic and readiness re-checks the migration state.',
  },
  {
    icon: ServerCog,
    title: 'Health-gated deploys',
    body: 'Deploy scripts validate configuration, wait for readiness, verify database and cache connectivity, and roll back on failure instead of leaving a half-updated service.',
  },
];

const OBSERVABILITY_FEATURES: Feature[] = [
  {
    icon: Gauge,
    title: 'Metrics that answer real questions',
    body: 'Dispatched totals by channel and status, delivery latency, live socket count, consumer lag, digest backlog and circuit-breaker trips.',
  },
  {
    icon: Webhook,
    title: 'Alerting on dead letters',
    body: 'Dead-lettered deliveries fire a Slack or PagerDuty compatible webhook with counters, so a delivery failure is actionable rather than silent.',
  },
  {
    icon: GitBranch,
    title: 'Gradual template rollout',
    body: 'A new template version can serve a percentage of users via a stable per-user hash, so copy changes ramp up instead of cutting over at once.',
  },
  {
    icon: Braces,
    title: 'Typed API surface',
    body: 'Every documented endpoint exists, is reachable from Swagger with bearer auth, and is consumed by the frontend through one typed service layer.',
  },
];

const CHANNELS: { icon: LucideIcon; label: string }[] = [
  { icon: Mail, label: 'Email (SES)' },
  { icon: MessageSquare, label: 'SMS (Twilio)' },
  { icon: Smartphone, label: 'Push (FCM)' },
  { icon: Bell, label: 'In-app inbox' },
  { icon: Webhook, label: 'WebSocket live' },
];

function Section({
  id,
  eyebrow,
  title,
  description,
  children,
  className,
}: {
  id: string;
  eyebrow: string;
  title: string;
  description?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section id={id} className={cn('scroll-mt-20 py-14 sm:py-20', className)} data-reveal>
      <div className="mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-8">
        <div className="max-w-2xl">
          <p className="text-xs font-medium uppercase tracking-wide text-primary">{eyebrow}</p>
          <h2 className="mt-2 text-2xl font-semibold tracking-tight text-balance sm:text-3xl">{title}</h2>
          {description ? (
            <p className="mt-3 text-pretty text-muted-foreground">{description}</p>
          ) : null}
        </div>
        <div className="mt-8">{children}</div>
      </div>
    </section>
  );
}

function FeatureGrid({ features }: { features: Feature[] }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2" data-reveal-group>
      {features.map((feature) => (
        <article
          key={feature.title}
          className="card-hover group rounded-xl border border-border bg-card p-5 shadow-sm"
        >
          <span
            aria-hidden="true"
            className="mb-3 inline-flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary/14"
          >
            <feature.icon className="size-4" />
          </span>
          <h3 className="text-sm font-semibold tracking-tight">{feature.title}</h3>
          <p className="mt-1.5 text-pretty text-sm text-muted-foreground">{feature.body}</p>
          {feature.detail ? (
            <p className="mt-2 font-mono text-xs text-muted-foreground">{feature.detail}</p>
          ) : null}
        </article>
      ))}
    </div>
  );
}

/**
 * Landing page (spec §8, §35). Copy describes only what this repository implements.
 * The hero shader sits behind everything, isolated in its own component, and never
 * intercepts pointer events.
 */
export function Landing() {
  const containerRef = useLandingIntro<HTMLDivElement>();

  return (
    <div ref={containerRef} className="min-h-dvh bg-background">
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>

      {/* ---------------- Header ---------------- */}
      <header className="sticky top-0 z-40 border-b border-transparent bg-background/80 backdrop-blur-md supports-[backdrop-filter]:bg-background/60">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-3 px-4 sm:px-6 lg:px-8">
          <Link
            to="/"
            className="flex items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <BrandMark className="size-8 shrink-0 rounded-[9px]" />
            <span className="text-sm font-semibold tracking-tight">Notify</span>
          </Link>

          <nav aria-label="Marketing" className="ml-6 hidden items-center gap-1 md:flex">
            {[
              { href: '#platform', label: 'Platform' },
              { href: '#delivery', label: 'Delivery' },
              { href: '#security', label: 'Security' },
              { href: '#operations', label: 'Operations' },
              { href: '#api', label: 'API' },
            ].map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="rounded-md px-2.5 py-1.5 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {item.label}
              </a>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <Button asChild variant="ghost" size="sm">
              <Link to="/login">Sign in</Link>
            </Button>
            <Button asChild size="sm">
              <Link to="/register">Get started</Link>
            </Button>
          </div>
        </div>
      </header>

      <main id="main-content">
        {/* ---------------- Hero ---------------- */}
        <section className="relative isolate overflow-hidden">
          <ShaderBackground className="-z-10" />

          <div className="mx-auto w-full max-w-6xl px-4 pb-16 pt-16 sm:px-6 sm:pt-24 lg:px-8">
            {/*
              `min-w-0` on both columns is load-bearing. Grid items default to
              `min-width: auto`, so the preview card's intrinsic width (from its
              nowrap channel badges) forced the track wider than the viewport. The page
              then overflowed and `overflow-x: hidden` silently *clipped* the hero copy
              rather than wrapping it — "honours each user's inte..." ran off the right
              edge at 390px. Without min-w-0 the grid refuses to shrink below content.
            */}
            <div className="grid items-center gap-12 lg:grid-cols-[1.05fr_0.95fr]">
              <div className="min-w-0">
                <p
                  data-intro="eyebrow"
                  className="inline-flex items-center gap-2 rounded-full border border-hero-border bg-[rgb(255_255_255/0.1)] px-3 py-1 text-xs font-medium text-hero-fg-muted"
                >
                  <span aria-hidden="true" className="size-1.5 rounded-full bg-hero-accent" />
                  Event-driven delivery platform
                </p>

                <h1
                  data-intro="headline"
                  className="mt-5 text-4xl font-semibold leading-[1.06] tracking-tight text-balance text-hero-fg sm:text-5xl lg:text-[3.5rem]"
                >
                  <span data-intro="line" className="block">
                    Reliable delivery for
                  </span>
                  <span data-intro="line" className="block text-hero-fg-muted">
                    every event your
                  </span>
                  <span data-intro="line" className="block">
                    product produces.
                  </span>
                </h1>

                <p
                  data-intro="description"
                  className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-hero-fg-muted sm:text-lg"
                >
                  Upstream services publish domain events. This platform validates them, honours
                  each user's intent and delivery preferences, then delivers across email, SMS, push
                  and live WebSocket channels — with retries, digests and full auditability built in.
                </p>

                <div data-intro="cta" className="mt-8 flex flex-wrap items-center gap-3">
                  <Button asChild size="lg">
                    <Link to="/register">
                      Get started
                      <ArrowRight aria-hidden="true" />
                    </Link>
                  </Button>
                  {/* `outline` paints a themed surface and border that disappear
                      against the dark hero band; `inverse` is the light-on-dark
                      equivalent. */}
                  <Button asChild variant="inverse" size="lg">
                    <Link to="/login">Sign in</Link>
                  </Button>
                </div>

                <ul
                  data-intro="trust"
                  className="mt-8 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-hero-fg-muted"
                >
                  {[
                    { icon: Lock, label: 'JWT + RBAC' },
                    { icon: ShieldCheck, label: 'Handshake-authenticated realtime' },
                    { icon: Activity, label: 'Prometheus metrics' },
                  ].map((item) => (
                    <li key={item.label} className="flex items-center gap-1.5">
                      <item.icon className="size-3.5" aria-hidden="true" />
                      {item.label}
                    </li>
                  ))}
                </ul>
              </div>

              {/* Product preview — a static, honest representation of the real UI */}
              <div data-intro="preview" className="relative min-w-0">
                <div className="rounded-xl border border-hero-border-strong bg-card/90 p-4 shadow-xl">
                  <div className="flex items-center gap-2 border-b border-border pb-3">
                    <span aria-hidden="true" className="size-2.5 rounded-full bg-muted" />
                    <span aria-hidden="true" className="size-2.5 rounded-full bg-muted" />
                    <span aria-hidden="true" className="size-2.5 rounded-full bg-muted" />
                    <span className="ml-2 font-mono text-[11px] text-muted-foreground">notification inbox</span>
                    <Badge variant="success" className="ml-auto text-[10px]">
                      live
                    </Badge>
                  </div>
                  <ul className="divide-y divide-border">
                    {[
                      { subject: 'Order placed · #A4821', channel: 'EMAIL', time: 'just now' },
                      { subject: 'Payment succeeded · $128.00', channel: 'PUSH', time: '2m ago' },
                      { subject: 'You were mentioned by a teammate', channel: 'WEBSOCKET', time: '6m ago' },
                      { subject: 'Order delivered · #A4821', channel: 'SMS', time: '1h ago' },
                    ].map((row) => (
                      <li key={row.subject} className="flex min-w-0 items-center gap-3 py-3">
                        <span aria-hidden="true" className="size-1.5 rounded-full bg-primary" />
                        <span className="min-w-0 flex-1 truncate text-sm font-medium">{row.subject}</span>
                        <Badge variant="muted" className="shrink-0 text-[10px]">
                          {row.channel}
                        </Badge>
                        <span className="shrink-0 text-[11px] text-muted-foreground tabular">{row.time}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="mt-3 flex flex-wrap justify-center gap-2">
                  {CHANNELS.map((channel) => (
                    <span
                      key={channel.label}
                      className="inline-flex items-center gap-1.5 rounded-full border border-hero-border bg-[rgb(255_255_255/0.1)] px-2.5 py-1 text-[11px] text-hero-fg-muted"
                    >
                      <channel.icon className="size-3" aria-hidden="true" />
                      {channel.label}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ---------------- Platform ---------------- */}
        <Section
          id="platform"
          eyebrow="Platform"
          title="From event to delivered notification"
          description="Four stages run for every inbound event, each one independently testable and observable."
        >
          <FeatureGrid features={PIPELINE_FEATURES} />
        </Section>

        {/* ---------------- Delivery ---------------- */}
        <Section
          id="delivery"
          eyebrow="Delivery"
          title="Respecting attention, not just preferences"
          description="Prefer a single notification to five. These controls are what keep the platform from becoming noise."
          className="border-y border-border bg-muted/25"
        >
          <FeatureGrid features={RELIABILITY_FEATURES} />
        </Section>

        {/* ---------------- Security ---------------- */}
        <Section
          id="security"
          eyebrow="Security"
          title="Authentication and authorization at every boundary"
          description="Isolation is enforced server-side. Hiding a menu item is a usability decision, never the control itself."
        >
          <FeatureGrid features={CONTROL_FEATURES} />
        </Section>

        {/* ---------------- Operations ---------------- */}
        <Section
          id="operations"
          eyebrow="Operations"
          title="Built to be run, not just demoed"
          description="Health probes, migrations, backups and rollback are part of the product, not an afterthought."
        >
          <FeatureGrid features={OBSERVABILITY_FEATURES} />
        </Section>

        {/* ---------------- API ---------------- */}
        <Section
          id="api"
          eyebrow="API"
          title="Every endpoint documented and usable from Swagger"
          description="JWT authentication works directly in the Swagger UI, so the API can be explored without writing a client first."
        >
          <div className="grid gap-4 lg:grid-cols-[1fr_1.1fr]">
            <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h3 className="text-sm font-semibold tracking-tight">Try the API</h3>
              <p className="mt-1.5 text-pretty text-sm text-muted-foreground">
                Register, then paste the returned token into Swagger's Authorize dialog. Every
                operation below exists in this repository — nothing is aspirational.
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                <Badge variant="outline" className="font-mono">
                  /swagger-ui.html
                </Badge>
                <Badge variant="outline" className="font-mono">
                  /v3/api-docs
                </Badge>
                <Badge variant="outline" className="font-mono">
                  /actuator/prometheus
                </Badge>
              </div>
            </div>

            <div className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
              <div className="flex items-center gap-2 border-b border-border bg-muted/40 px-4 py-2.5">
                <span className="font-mono text-[11px] text-muted-foreground">POST</span>
                <code className="truncate font-mono text-xs">/api/v1/auth/login</code>
              </div>
              <pre className="overflow-x-auto bg-muted/20 p-4 font-mono text-xs leading-relaxed">
{`{
  "email": "you@example.com",
  "password": "••••••••"
}

→ 200
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "3f1c2b4a-...",
  "email": "you@example.com",
  "displayName": "Your name",
  "roles": ["USER"]
}`}
              </pre>
            </div>
          </div>
        </Section>

        {/* ---------------- Final CTA ---------------- */}
        <section className="border-t border-border" data-reveal>
          <div className="mx-auto w-full max-w-3xl px-4 py-16 text-center sm:px-6 sm:py-20 lg:px-8">
            <h2 className="text-2xl font-semibold tracking-tight text-balance sm:text-3xl">
              See the delivery pipeline for yourself
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-pretty text-muted-foreground">
              Create an account, publish an event, and watch it arrive in the live inbox. Everything
              runs from a single Docker Compose stack.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <Button asChild size="lg">
                <Link to="/register">
                  Create your account
                  <ArrowRight aria-hidden="true" />
                </Link>
              </Button>
              <Button asChild variant="outline" size="lg">
                <Link to="/login">Sign in</Link>
              </Button>
            </div>
          </div>
        </section>
      </main>

      {/* ---------------- Footer ---------------- */}
      <footer className="border-t border-border bg-muted/25">
        <div className="mx-auto w-full max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
            <div className="lg:col-span-2">
              <div className="flex items-center gap-2.5">
                <BrandMark className="size-8 shrink-0 rounded-[9px]" />
                <span className="text-sm font-semibold tracking-tight">Notify</span>
              </div>
              <p className="mt-3 max-w-sm text-pretty text-sm text-muted-foreground">
                A multi-channel notification platform: Kafka event ingestion, preference-aware
                routing, digests, frequency caps, realtime WebSocket delivery and full
                observability.
              </p>
            </div>

            {[
              {
                title: 'Platform',
                links: [
                  { href: '#platform', label: 'How it works' },
                  { href: '#delivery', label: 'Delivery controls' },
                  { href: '#operations', label: 'Operations' },
                ],
              },
              {
                title: 'Resources',
                links: [
                  { href: '/swagger-ui.html', label: 'API reference' },
                  { href: '/actuator/health', label: 'Health' },
                  { href: '/actuator/prometheus', label: 'Metrics' },
                ],
              },
            ].map((column) => (
              <div key={column.title}>
                <h2 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {column.title}
                </h2>
                <ul className="mt-3 space-y-2">
                  {column.links.map((link) => (
                    <li key={link.label}>
                      {link.href.startsWith('#') || link.href.startsWith('/') ? (
                        <a
                          href={link.href}
                          className="text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          {link.label}
                        </a>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="mt-10 flex flex-col gap-2 border-t border-border pt-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <p>
              Built with Spring Boot, Kafka, PostgreSQL, Redis and React.{' '}
              {motionEnabled() ? '' : 'Reduced motion is enabled in your system settings.'}
            </p>
            <p className="tabular">JWT · RBAC · Flyway · Prometheus</p>
          </div>
        </div>
      </footer>
    </div>
  );
}


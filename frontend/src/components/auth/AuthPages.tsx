import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AlertTriangle, Eye, EyeOff, Layers, Lock, Mail } from 'lucide-react';
import { usePageEnter } from '@/animations/transitions';
import { useAuth } from '@/stores/auth';
import { normalizeError, type NormalizedError } from '@/services/errors';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

/** Shared split-screen auth layout: form on the left, product context on the right. */
function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}) {
  const containerRef = usePageEnter<HTMLDivElement>();

  return (
    <div className="grid min-h-dvh lg:grid-cols-2" ref={containerRef}>
      <main className="flex items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-sm">
          <Link
            to="/"
            className="mb-8 inline-flex items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <span
              aria-hidden="true"
              className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground"
            >
              <Layers className="size-4" />
            </span>
            <span className="text-sm font-semibold tracking-tight">Notify</span>
          </Link>

          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1.5 text-sm text-muted-foreground">{subtitle}</p>

          <div className="mt-7">{children}</div>
          <div className="mt-6 text-sm text-muted-foreground">{footer}</div>
        </div>
      </main>

      {/* Contextual panel: hidden on small screens so the form stays the focus. */}
      <aside className="relative hidden overflow-hidden border-l border-border bg-muted/30 lg:block">
        <div aria-hidden="true" className="absolute inset-0 bg-grid mask-radial-fade opacity-60" />
        <div className="relative flex h-full flex-col justify-end gap-6 p-10">
          <blockquote className="max-w-md space-y-3">
            <p className="text-pretty text-lg font-medium leading-relaxed tracking-tight">
              Upstream services publish domain events. This platform turns them into email, SMS,
              push and live in-app notifications that respect each user's intent.
            </p>
            <footer className="text-sm text-muted-foreground">
              Kafka ingestion · preference-aware routing · digests · WebSocket delivery
            </footer>
          </blockquote>
          <ul className="grid gap-2 text-sm text-muted-foreground">
            {[
              'JWT authentication with BCrypt-12 password hashing',
              'Role-based access control enforced server-side',
              'Retries, dead-letter routing and circuit breakers',
            ].map((item) => (
              <li key={item} className="flex items-start gap-2">
                <Lock className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden="true" />
                {item}
              </li>
            ))}
          </ul>
        </div>
      </aside>
    </div>
  );
}

/** Inline form-level error with an icon + retry-safe copy (spec §15). */
function FormError({ error }: { error: NormalizedError | null }) {
  if (!error) return null;
  return (
    <div
      role="alert"
      className="flex items-start gap-2.5 rounded-lg border border-destructive/30 bg-destructive/8 p-3 text-sm text-destructive"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>
        <span className="font-medium">{error.title}.</span>{' '}
        {error.kind === 'network' ? 'Check that the backend is running.' : error.message}
      </span>
    </div>
  );
}

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const next: Record<string, string> = {};
    if (!email.trim()) next.email = 'Enter your email address.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) next.email = 'Enter a valid email address.';
    if (!password) next.password = 'Enter your password.';
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      setFieldErrors(normalized.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Access your notification workspace."
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <FormError error={error} />

        <div className="space-y-1.5">
          <label htmlFor="login-email" className="text-sm font-medium">
            Email
          </label>
          <Input
            id="login-email"
            type="email"
            autoComplete="email"
            required
            placeholder="you@example.com"
            icon={<Mail />}
            value={email}
            error={fieldErrors.email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="login-password" className="text-sm font-medium">
            Password
          </label>
          <div className="relative">
            <Input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              required
              placeholder="••••••••"
              className="pr-10"
              value={password}
              error={fieldErrors.password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
            </button>
          </div>
        </div>

        <Button type="submit" className="w-full" loading={submitting} loadingText="Signing in…">
          Sign in
        </Button>
      </form>
    </AuthLayout>
  );
}

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<NormalizedError | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const next: Record<string, string> = {};
    if (!displayName.trim()) next.displayName = 'Enter a name to show in the workspace.';
    if (!email.trim()) next.email = 'Enter your email address.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) next.email = 'Enter a valid email address.';
    // Backend RegisterRequest enforces 8+ chars; mirror it client-side to avoid a round trip.
    if (password.length < 8) next.password = 'Use at least 8 characters.';
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      await register(email.trim(), password, displayName.trim());
      navigate('/', { replace: true });
    } catch (caught) {
      const normalized = normalizeError(caught);
      setError(normalized);
      setFieldErrors((current) => ({ ...current, ...normalized.fieldErrors }));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Start receiving notifications in seconds."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <FormError error={error} />

        <div className="space-y-1.5">
          <label htmlFor="register-name" className="text-sm font-medium">
            Display name
          </label>
          <Input
            id="register-name"
            autoComplete="name"
            required
            placeholder="Ada Lovelace"
            value={displayName}
            error={fieldErrors.displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="register-email" className="text-sm font-medium">
            Email
          </label>
          <Input
            id="register-email"
            type="email"
            autoComplete="email"
            required
            placeholder="you@example.com"
            icon={<Mail />}
            value={email}
            error={fieldErrors.email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="register-password" className="text-sm font-medium">
            Password
          </label>
          <div className="relative">
            <Input
              id="register-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              required
              minLength={8}
              placeholder="At least 8 characters"
              className="pr-10"
              value={password}
              error={fieldErrors.password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
            </button>
          </div>
          <p className="text-xs text-muted-foreground">
            Stored with BCrypt at cost 12. Passwords are never logged or returned by the API.
          </p>
        </div>

        <Button type="submit" className="w-full" loading={submitting} loadingText="Creating account…">
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
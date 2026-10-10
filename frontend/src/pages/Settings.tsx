import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  KeyRound,
  LogOut,
  Monitor,
  Moon,
  Palette,
  Shield,
  Sun,
  Trash2,
  User,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/stores/auth';
import { useTheme } from '@/stores/theme';
import type { ThemePreference } from '@/stores/theme';
import { useRealtime } from '@/stores/realtime';
import { TOKEN_STORAGE_KEY } from '@/services/api-client';
import { cn } from '@/lib/utils';
import { PageHeader } from '@/components/layout/primitives';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { toast } from '@/components/ui/toaster';

/**
 * Settings centre (spec §26): tabs on desktop, stacked navigation on mobile.
 *
 * Every panel here is backed by something real:
 *  - Profile   → the identity the JWT carries (read-only; there is no profile-write
 *                endpoint in this backend, and inventing one is out of scope)
 *  - Security  → the live session: roles, token expiry, WebSocket state
 *  - Preferences → theme, persisted locally
 *  - Danger zone → sign out, and clear local session storage
 *
 * Sections the specification lists that have no backend counterpart (password change,
 * workspace, roles management, member management, billing) are intentionally absent
 * rather than rendered as non-functional UI. See docs/UI-UX.md.
 */
type SectionId = 'profile' | 'security' | 'preferences' | 'danger';

const SECTIONS: { id: SectionId; label: string; icon: typeof User; description: string }[] = [
  { id: 'profile', label: 'Profile', icon: User, description: 'Your account identity' },
  { id: 'security', label: 'Security', icon: Shield, description: 'Session, roles and live channel' },
  { id: 'preferences', label: 'Preferences', icon: Palette, description: 'Theme and appearance' },
  { id: 'danger', label: 'Danger zone', icon: Trash2, description: 'Sign out and stored data' },
];

/**
 * Reads the `exp` claim from the JWT so the session panel can tell the user when they
 * will be asked to sign in again. The backend issues a stateless token with no refresh
 * flow, so this is the only forward-looking signal available — and it is read from the
 * token the client already holds, not from a new endpoint.
 */
function readTokenExpiry(token: string): Date | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(normalized)) as { exp?: number };
    return typeof claims.exp === 'number' ? new Date(claims.exp * 1000) : null;
  } catch {
    return null;
  }
}

export function Settings() {
  const { user, logout } = useAuth();
  const { preference, setTheme } = useTheme();
  const { connected } = useRealtime();
  const navigate = useNavigate();

  const [section, setSection] = useState<SectionId>('profile');
  const [confirmSignOut, setConfirmSignOut] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [tokenState, setTokenState] = useState<{ expiresAt: Date | null; error: string | null }>({
    expiresAt: null,
    error: null,
  });

  const loadTokenState = useCallback(async () => {
    setTokenState({ expiresAt: null, error: null });
    try {
      const stored = localStorage.getItem(TOKEN_STORAGE_KEY);
      setTokenState({
        expiresAt: stored ? readTokenExpiry(stored) : null,
        error: null,
      });
    } catch {
      setTokenState({ expiresAt: null, error: 'Session storage is unavailable in this browser.' });
    }
  }, []);

  useEffect(() => {
    void loadTokenState();
  }, [loadTokenState]);

  const expiryLabel = useMemo(() => {
    if (!tokenState.expiresAt) return null;
    return tokenState.expiresAt.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  }, [tokenState.expiresAt]);

  const signOut = async () => {
    setSigningOut(true);
    try {
      logout();
      setConfirmSignOut(false);
      navigate('/login', { replace: true });
      toast('Signed out', { description: 'Your session was cleared from this browser.' });
    } finally {
      setSigningOut(false);
    }
  };

  const renderPanel = () => {
    switch (section) {
      case 'profile':
        return (
          <Card>
            <CardHeader>
              <CardTitle>Profile</CardTitle>
              <CardDescription>
                The identity attached to your session. It is set at registration.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <dl className="space-y-3 text-sm">
                {[
                  { label: 'Display name', value: user?.displayName || '—' },
                  { label: 'Email', value: user?.email || '—' },
                  { label: 'User ID', value: <code className="font-mono text-xs">{user?.userId}</code> },
                ].map((row) => (
                  <div
                    key={row.label}
                    className="flex flex-col gap-1 border-b border-border pb-3 last:border-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <dt className="text-muted-foreground">{row.label}</dt>
                    <dd className="min-w-0 break-all font-medium sm:text-right">{row.value}</dd>
                  </div>
                ))}
              </dl>
              <p className="mt-4 rounded-md border border-border bg-muted/50 p-3 text-xs text-muted-foreground">
                Editing your profile requires an account endpoint that this deployment does not
                expose. Your display name and email are managed through registration.
              </p>
            </CardContent>
          </Card>
        );

      case 'security':
        return (
          <>
            <Card>
              <CardHeader>
                <CardTitle>Session</CardTitle>
                <CardDescription>
                  This backend issues a stateless bearer token. There is no refresh flow, so
                  you sign in again when it expires.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {tokenState.error ? (
                  <ErrorState kind="unknown" message={tokenState.error} compact />
                ) : null}
                <dl className="space-y-3 text-sm">
                  <div className="flex flex-col gap-1 border-b border-border pb-3 sm:flex-row sm:items-center sm:justify-between">
                    <dt className="text-muted-foreground">Token expires</dt>
                    <dd className="font-medium tabular">{expiryLabel ?? '—'}</dd>
                  </div>
                  <div className="flex flex-col gap-1 border-b border-border pb-3 sm:flex-row sm:items-center sm:justify-between">
                    <dt className="text-muted-foreground">Live channel</dt>
                    <dd className="font-medium">
                      {connected ? 'Connected' : 'Disconnected'}
                    </dd>
                  </div>
                  <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                    <dt className="text-muted-foreground">Granted roles</dt>
                    <dd className="flex flex-wrap justify-end gap-1">
                      {(user?.roles ?? []).length === 0 ? (
                        <span className="text-muted-foreground">None</span>
                      ) : (
                        (user?.roles ?? []).map((role) => (
                          <Badge key={role} variant={role === 'ADMIN' ? 'default' : 'muted'}>
                            {role}
                          </Badge>
                        ))
                      )}
                    </dd>
                  </div>
                </dl>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Credentials</CardTitle>
                <CardDescription>How this session authenticates against the API.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {[
                  {
                    label: 'Transport',
                    value: 'JWT bearer token in the Authorization header',
                  },
                  {
                    label: 'Real-time channel',
                    value: 'Same token, passed as a WebSocket query parameter',
                  },
                  {
                    label: 'Storage',
                    value: 'localStorage — readable by scripts on this origin',
                  },
                ].map((row) => (
                  <div key={row.label} className="flex flex-col gap-0.5 sm:flex-row sm:gap-4">
                    <span className="shrink-0 text-muted-foreground sm:w-40">{row.label}</span>
                    <span className="text-pretty">{row.value}</span>
                  </div>
                ))}
                <div className="rounded-md border border-border bg-muted/50 p-3">
                  <p className="text-xs text-muted-foreground">
                    The complete token is never displayed here and cannot be recovered after you
                    navigate away — sign in again to obtain a new one.
                  </p>
                </div>
              </CardContent>
            </Card>
          </>
        );

      case 'preferences':
        return (
          <Card>
            <CardHeader>
              <CardTitle>Appearance</CardTitle>
              <CardDescription>
                Theme is stored in this browser and applied before the first paint, so there is no
                flash of the wrong theme.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <fieldset>
                <legend className="text-sm font-medium">Theme</legend>
                <p className="mt-1 text-xs text-muted-foreground">
                  System follows your operating system setting live.
                </p>
                <div
                  role="radiogroup"
                  aria-label="Theme"
                  className="mt-3 grid gap-2 sm:grid-cols-3"
                >
                  {(
                    [
                      { value: 'light' as const, label: 'Light', icon: Sun },
                      { value: 'dark' as const, label: 'Dark', icon: Moon },
                      { value: 'system' as const, label: 'System', icon: Monitor },
                    ]
                  ).map((option) => {
                    const selected = preference === option.value;
                    return (
                      <button
                        key={option.value}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        onClick={() => setTheme(option.value as ThemePreference)}
                        className={cn(
                          'flex items-center gap-2.5 rounded-lg border p-3 text-sm font-medium transition-colors',
                          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                          selected
                            ? 'border-primary bg-primary/5 text-foreground'
                            : 'border-border hover:bg-accent/60',
                        )}
                      >
                        <option.icon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                        {option.label}
                        {selected ? (
                          <span className="ml-auto text-xs font-normal text-primary">Active</span>
                        ) : null}
                      </button>
                    );
                  })}
                </div>
              </fieldset>

              <div className="mt-6 border-t border-border pt-4">
                <p className="text-sm font-medium">Motion</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Animations follow your operating system <code className="font-mono">prefers-reduced-motion</code>{' '}
                  setting. When reduced motion is on, entrance animations and the hero shader
                  render as a single static frame while all functional behaviour is preserved.
                </p>
              </div>
            </CardContent>
          </Card>
        );

      case 'danger':
        return (
          <Card className="border-destructive/40">
            <CardHeader>
              <CardTitle className="text-destructive">Danger zone</CardTitle>
              <CardDescription>
                Actions here end your session. Notification history and preferences are stored
                server-side and are not affected.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-col gap-3 rounded-lg border border-border p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="flex items-center gap-2 text-sm font-medium">
                    <LogOut className="size-4 text-muted-foreground" aria-hidden="true" />
                    Sign out
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Clears the stored token and returns you to the sign-in page.
                  </p>
                </div>
                <Button variant="destructive" onClick={() => setConfirmSignOut(true)}>
                  Sign out
                </Button>
              </div>

              <div className="flex flex-col gap-3 rounded-lg border border-border p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="flex items-center gap-2 text-sm font-medium">
                    <KeyRound className="size-4 text-muted-foreground" aria-hidden="true" />
                    Account password
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Password changes are not exposed over HTTP in this deployment.
                  </p>
                </div>
                <Button variant="outline" disabled title="Not available in this deployment">
                  Change password
                </Button>
              </div>
            </CardContent>
          </Card>
        );

      default:
        return null;
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Account"
        title="Settings"
        description="Manage your session, appearance and account security."
        breadcrumbs={[{ label: 'Workspace', href: '/dashboard' }, { label: 'Settings' }]}
      />

      {/* Tabs on desktop, a stacked rail on mobile — same content, no duplicate DOM. */}
      <div className="grid gap-6 lg:grid-cols-[13rem_1fr]">
        <nav aria-label="Settings sections">
          {/* Horizontal scroller on mobile keeps the header compact without a drawer. */}
          <ul className="-mx-4 flex gap-1 overflow-x-auto px-4 pb-1 lg:mx-0 lg:block lg:space-y-1 lg:overflow-visible lg:px-0">
            {SECTIONS.map((item) => {
              const selected = section === item.id;
              return (
                <li key={item.id} className="shrink-0 lg:shrink">
                  <button
                    type="button"
                    aria-current={selected ? 'page' : undefined}
                    onClick={() => setSection(item.id)}
                    className={cn(
                      'flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-left text-sm font-medium transition-colors',
                      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                      selected
                        ? 'bg-accent text-accent-foreground'
                        : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground',
                    )}
                  >
                    <item.icon className="size-4 shrink-0" aria-hidden="true" />
                    <span className="whitespace-nowrap">{item.label}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="min-w-0 space-y-4">{renderPanel()}</div>
      </div>

      <Dialog open={confirmSignOut} onOpenChange={setConfirmSignOut}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Sign out of Notify?</DialogTitle>
            <DialogDescription>
              Your stored session will be cleared from this browser. Any notification history and
              delivery preferences stay on the server.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmSignOut(false)} disabled={signingOut}>
              Stay signed in
            </Button>
            <Button variant="destructive" onClick={() => void signOut()} loading={signingOut} loadingText="Signing out…">
              Sign out
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
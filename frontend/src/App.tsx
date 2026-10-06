import { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/stores/auth';
import { ThemeProvider } from '@/stores/theme';
import { RealtimeProvider } from '@/stores/realtime';
import { TooltipProvider } from '@/components/ui/tooltip';
import { AppToaster } from '@/components/ui/toaster';
import { PageSkeleton } from '@/components/ui/skeletons';
import { AppShell } from '@/components/layout/AppShell';
import { Login, Register } from '@/components/auth/AuthPages';
import { Landing } from '@/components/landing/Landing';
import { Dashboard } from '@/pages/Dashboard';
import { NotificationCenter } from '@/pages/NotificationCenter';
import { Preferences } from '@/pages/Preferences';
import { Settings } from '@/pages/Settings';
import { SystemStatus } from '@/pages/SystemStatus';

/**
 * Routes are declared flat at the top level and the shell is a layout route that
 * renders `<Outlet />`. They used to be nested under `/dashboard`, which meant every
 * link pointing at `/inbox` or `/preferences` fell through to the catch-all and
 * silently redirected — the entire navigation was dead.
 */
const AdminDashboard = lazy(() =>
  import('@/pages/AdminDashboard').then((module) => ({ default: module.AdminDashboard })),
);

/** Blocks rendering until the stored session has been validated against the API. */
function RequireAuth() {
  const { user, checking } = useAuth();
  if (checking) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <PageSkeleton />
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return <Outlet />;
}

/**
 * Route-level admin guard. The server enforces ROLE_ADMIN on every admin endpoint; this
 * only avoids rendering a shell the user cannot use (spec §7).
 */
function RequireAdmin() {
  const { user, isAdmin, checking } = useAuth();
  if (checking) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (!isAdmin) return <Navigate to="/dashboard" replace />;
  return <Outlet />;
}

function AuthenticatedRoutes() {
  const { user } = useAuth();

  // One socket for the whole app (spec §21). Mounted here so the public landing and
  // auth pages never open a WebSocket, and no page mounts a second one.
  return (
    <RealtimeProvider enabled={Boolean(user)}>
      <Suspense fallback={<PageSkeleton />}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/inbox" element={<NotificationCenter />} />
            <Route path="/preferences" element={<Preferences />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/status" element={<SystemStatus />} />
            <Route element={<RequireAdmin />}>
              <Route path="/admin" element={<AdminDashboard />} />
            </Route>
          </Route>
          {/* Any other in-app URL lands on the dashboard rather than a bare 404. */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Suspense>
    </RealtimeProvider>
  );
}

function RootRedirect() {
  const { user, checking } = useAuth();
  if (checking) return <PageSkeleton />;
  return user ? <Navigate to="/dashboard" replace /> : <Navigate to="/welcome" replace />;
}

function AppRoutes() {
  const { user } = useAuth();

  return (
    <Routes>
      <Route path="/welcome" element={<Landing />} />
      <Route path="/login" element={user ? <Navigate to="/dashboard" replace /> : <Login />} />
      <Route path="/register" element={user ? <Navigate to="/dashboard" replace /> : <Register />} />
      <Route element={<RequireAuth />}>
        <Route path="*" element={<AuthenticatedRoutes />} />
      </Route>
      {/* Legacy deep links kept working so old bookmarks don't 404. */}
      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <AuthProvider>
          <TooltipProvider delayDuration={200}>
            <AppRoutes />
            <AppToaster />
          </TooltipProvider>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
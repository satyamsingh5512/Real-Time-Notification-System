import { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/stores/auth';
import { TooltipProvider } from '@/components/ui/tooltip';
import { AppToaster } from '@/components/ui/toaster';
import { Skeleton, SkeletonStat, SkeletonText } from '@/components/ui/skeleton';
import { AppShell } from '@/components/layout/AppShell';
import { Login, Register } from '@/components/auth/AuthPages';
import { Landing } from '@/components/landing/Landing';
import { Dashboard } from '@/pages/Dashboard';
import { NotificationCenter } from '@/pages/NotificationCenter';
import { Preferences } from '@/pages/Preferences';

/**
 * The admin console is the only heavy route, so it is split out: the initial bundle stays
 * small for the landing page and the authenticated shell (spec §30).
 */
const AdminDashboard = lazy(() =>
  import('@/pages/AdminDashboard').then((module) => ({ default: module.AdminDashboard })),
);

function FullPageSkeleton() {
  return (
    <div className="space-y-6" aria-hidden="true">
      <div className="space-y-2">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-8 w-72" />
        <SkeletonText lines={2} className="max-w-xl" />
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, index) => (
          <SkeletonStat key={index} />
        ))}
      </div>
      <Skeleton className="h-64 w-full rounded-xl" />
    </div>
  );
}

/** Blocks rendering until the stored session has been validated against the API. */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, checking } = useAuth();
  const location = useLocation();

  if (checking) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <FullPageSkeleton />
      </div>
    );
  }
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  return <>{children}</>;
}

/**
 * Route-level admin guard. The server enforces ROLE_ADMIN on every admin endpoint; this
 * only avoids rendering a shell the user cannot use (spec §7).
 */
function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { user, isAdmin, checking } = useAuth();
  if (checking) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function AuthenticatedRoutes() {
  return (
    <AppShell>
      <Suspense fallback={<FullPageSkeleton />}>
        <Routes>
          <Route index element={<Dashboard />} />
          <Route path="inbox" element={<NotificationCenter />} />
          <Route path="preferences" element={<Preferences />} />
          <Route
            path="admin"
            element={
              <RequireAdmin>
                <AdminDashboard />
              </RequireAdmin>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </AppShell>
  );
}

function RootRedirect() {
  const { user, checking } = useAuth();
  if (checking) return <FullPageSkeleton />;
  return user ? <Navigate to="/dashboard" replace /> : <Navigate to="/welcome" replace />;
}

function AppRoutes() {
  const { user, checking } = useAuth();

  return (
    <Routes>
      <Route path="/welcome" element={<Landing />} />
      <Route path="/login" element={user ? <Navigate to="/dashboard" replace /> : <Login />} />
      <Route path="/register" element={user ? <Navigate to="/dashboard" replace /> : <Register />} />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            {checking ? <FullPageSkeleton /> : <AuthenticatedRoutes />}
          </RequireAuth>
        }
      />
      {/* Legacy deep links kept working so old bookmarks don't 404. */}
      <Route path="/*" element={<RootRedirect />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <TooltipProvider delayDuration={200}>
          <AppRoutes />
          <AppToaster />
        </TooltipProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
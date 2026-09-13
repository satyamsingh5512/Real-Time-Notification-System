import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { AppShell } from './components/AppShell';
import { useRealtime } from './realtime/useRealtime';
import { AdminDashboard } from './pages/AdminDashboard';
import { Dashboard } from './pages/Dashboard';
import { Login } from './pages/Login';
import { NotificationCenter } from './pages/NotificationCenter';
import { Preferences } from './pages/Preferences';
import { Register } from './pages/Register';
import type { JSX } from 'react';

function RequireAuth({ children }: { children: JSX.Element }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const { user, isAdmin } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!isAdmin) return <Navigate to="/" replace />;
  return children;
}

function AuthedApp() {
  const { user } = useAuth();
  const { unread, toasts, connected, refreshUnread, pushToast, dismissToast } = useRealtime(!!user);

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <AppShell unread={unread} connected={connected} toasts={toasts} onDismissToast={dismissToast}>
              <Routes>
                <Route path="/" element={<Dashboard unread={unread} connected={connected} />} />
                <Route path="/inbox" element={<NotificationCenter onChanged={() => void refreshUnread()} />} />
                <Route path="/preferences" element={<Preferences notify={pushToast} />} />
                <Route
                  path="/admin"
                  element={
                    <RequireAdmin>
                      <AdminDashboard notify={pushToast} />
                    </RequireAdmin>
                  }
                />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </AppShell>
          </RequireAuth>
        }
      />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AuthedApp />
    </AuthProvider>
  );
}

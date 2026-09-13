import { NavLink, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import type { Toast } from '../realtime/useRealtime';

interface ShellProps {
  children: ReactNode;
  unread: number;
  connected: boolean;
  toasts: Toast[];
  onDismissToast: (id: number) => void;
}

const ROWS = [
  { to: '/', label: 'Dashboard', ico: '◧' },
  { to: '/inbox', label: 'Notification Center', ico: '✉' },
  { to: '/preferences', label: 'Preferences', ico: '☷' },
];

const ADMIN_ROWS = [{ to: '/admin', label: 'Admin Dashboard', ico: '⬣' }];

export function AppShell({ children, unread, connected, toasts, onDismissToast }: ShellProps) {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <>
      <nav className="nt-nav">
        <div className="nt-nav-inner">
          <NavLink to="/" className="nt-wordmark">
            <span className="nt-wordmark-dot">◍</span> Notify
          </NavLink>
          <div className="nt-nav-links">
            <NavLink to="/" className={({ isActive }) => (isActive ? 'active' : '')}>
              Dashboard
            </NavLink>
            <NavLink to="/inbox" className={({ isActive }) => (isActive ? 'active' : '')}>
              Inbox
            </NavLink>
            {isAdmin && (
              <NavLink to="/admin" className={({ isActive }) => (isActive ? 'active' : '')}>
                Admin
              </NavLink>
            )}
          </div>
          <div className="nt-nav-right">
            <span className="nt-caption" title={connected ? 'Live' : 'Offline'}>
              {connected ? '● live' : '○ offline'}
            </span>
            <button
              className="nt-bell"
              title="Open inbox"
              onClick={() => navigate('/inbox')}
              style={{ borderColor: connected ? 'var(--nt-primary)' : undefined }}
            >
              🔔
              {unread > 0 && <span className="nt-bell-count">{unread > 99 ? '99+' : unread}</span>}
            </button>
            <span className="nt-nav-user">{user?.displayName ?? user?.email}</span>
            <button className="nt-btn nt-btn-utility" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </div>
      </nav>

      <div className="nt-container nt-page">
        <div className="nt-shell">
          <aside className="nt-sidebar">
            {ROWS.map((r) => (
              <NavLink key={r.to} to={r.to} className={({ isActive }) => `nt-side-row${isActive ? ' active' : ''}`} end={r.to === '/'}>
                <span className="ico">{r.ico}</span>
                {r.label}
              </NavLink>
            ))}
            {isAdmin &&
              ADMIN_ROWS.map((r) => (
                <NavLink key={r.to} to={r.to} className={({ isActive }) => `nt-side-row${isActive ? ' active' : ''}`}>
                  <span className="ico">{r.ico}</span>
                  {r.label}
                </NavLink>
              ))}
          </aside>
          <main className="nt-main">{children}</main>
        </div>
      </div>

      <footer className="nt-footer">
        <div className="nt-container">
          Real-Time Notification System · WebSocket live delivery ·{' '}
          <span className="nt-caption">Notion-inspired UI (DESIGN.md)</span>
        </div>
      </footer>

      <div className="nt-toasts">
        {toasts.map((t) => (
          <div key={t.id} className="nt-toast" role="status">
            <span className="nt-dot" style={{ background: 'var(--nt-primary)', marginTop: 6 }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600 }}>{t.title}</div>
              <div className="nt-muted" style={{ fontSize: 14 }}>{t.body}</div>
            </div>
            <button className="nt-btn nt-btn-utility" onClick={() => onDismissToast(t.id)}>
              ✕
            </button>
          </div>
        ))}
      </div>
    </>
  );
}

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Activity,
  Bell,
  LayoutDashboard,
  LogOut,
  Menu,
  Monitor,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  Server,
  Settings,
  Shield,
  SlidersHorizontal,
  Sun,
  X,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useAuth } from '@/stores/auth';
import { useTheme } from '@/stores/theme';
import { useRealtime } from '@/stores/realtime';
import { useHotkey } from '@/hooks/useHotkey';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { UserAvatar } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { AppTooltip } from '@/components/ui/tooltip';
import { BrandMark } from '@/components/brand/BrandMark';
import { CommandPalette } from './CommandPalette';
import { NotificationDrawer } from '@/components/notifications/NotificationDrawer';

/**
 * Navigation model (spec §7).
 *
 * Every entry maps to an endpoint that actually exists. This deployment is a
 * notification platform — there is no project, team, member, file, plan, API-key or
 * audit surface in the backend, so those are deliberately absent rather than rendered
 * as dead or faked pages (spec §32). `requiresAdmin` drives permission-aware visibility
 * so a non-admin user never sees admin surfaces in the UI — and RBAC is still enforced
 * server-side regardless.
 */
interface NavItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  requiresAdmin?: boolean;
  end?: boolean;
  description: string;
}

export const NAV_SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: 'Workspace',
    items: [
      {
        to: '/dashboard',
        label: 'Overview',
        icon: LayoutDashboard,
        end: true,
        description: 'Live delivery overview',
      },
      {
        to: '/inbox',
        label: 'Notifications',
        icon: Bell,
        description: 'Inbox, read state and realtime feed',
      },
      {
        to: '/preferences',
        label: 'Delivery preferences',
        icon: SlidersHorizontal,
        description: 'Intent, channels, quiet hours, digests',
      },
    ],
  },
  {
    title: 'Account',
    items: [
      {
        to: '/settings',
        label: 'Settings',
        icon: Settings,
        description: 'Profile, security, appearance, session',
      },
      {
        to: '/status',
        label: 'System status',
        icon: Server,
        description: 'Liveness, readiness and live sessions',
      },
    ],
  },
  {
    title: 'Administration',
    items: [
      {
        to: '/admin',
        label: 'Admin console',
        icon: Shield,
        requiresAdmin: true,
        description: 'Broadcasts, delivery metrics, templates',
      },
    ],
  },
];

export function AppShell({ children }: { children?: ReactNode }) {
  const { user, isAdmin, logout } = useAuth();
  const { preference, setTheme, toggle } = useTheme();
  const { unread, connected } = useRealtime();
  const navigate = useNavigate();
  const location = useLocation();

  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const mobilePanelRef = useRef<HTMLElement>(null);

  // ⌘K / Ctrl+K is advertised in the topbar, so it has to actually be bound (spec §17).
  const openPalette = useCallback(() => setPaletteOpen(true), []);
  useHotkey('mod+k', openPalette);

  // The command palette's "Notification drawer" entry reaches the shell through an
  // event rather than lifting the drawer state into a third store.
  useEffect(() => {
    const onOpen = () => setDrawerOpen(true);
    window.addEventListener('rtns:open-notifications', onOpen);
    return () => window.removeEventListener('rtns:open-notifications', onOpen);
  }, []);

  // Mobile slide-over must never survive a navigation. `location` is the router's
  // location — this used to read the global `window.location`, which is not reactive,
  // so the drawer stayed open across in-app navigation.
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  // Lock body scroll while the slide-over is open so the page behind doesn't move.
  useEffect(() => {
    if (!mobileOpen) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [mobileOpen]);

  /**
   * The slide-over is a modal dialog, so it owes the user a focus trap and Escape —
   * it was hand-rolled rather than Radix, which silently skipped both.
   */
  useEffect(() => {
    if (!mobileOpen) return;
    const panel = mobilePanelRef.current;
    const selector =
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    const focusables = () =>
      Array.from(panel?.querySelectorAll<HTMLElement>(selector) ?? []).filter(
        (element) => element.offsetParent !== null,
      );

    // Move focus into the panel so the keyboard user lands inside it, not behind it.
    const first = focusables()[0];
    first?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        setMobileOpen(false);
        return;
      }
      if (event.key !== 'Tab') return;

      const items = focusables();
      if (items.length === 0) return;
      const firstItem = items[0];
      const lastItem = items[items.length - 1];

      if (event.shiftKey && document.activeElement === firstItem) {
        event.preventDefault();
        lastItem.focus();
      } else if (!event.shiftKey && document.activeElement === lastItem) {
        event.preventDefault();
        firstItem.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown, true);
    return () => document.removeEventListener('keydown', onKeyDown, true);
  }, [mobileOpen]);

  const sections = useMemo(
    () =>
      NAV_SECTIONS.map((section) => ({
        ...section,
        items: section.items.filter((item) => !item.requiresAdmin || isAdmin),
      })).filter((section) => section.items.length > 0),
    [isAdmin],
  );

  const sidebar = (
    <div className="flex h-full flex-col">
      <div className={cn('flex items-center gap-2.5 px-4', collapsed ? 'justify-center py-4' : 'h-14 py-0')}>
        <Link
          to="/dashboard"
          className="flex min-w-0 items-center gap-2.5 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={collapsed ? 'Notify — go to overview' : undefined}
        >
          <BrandMark className="size-8 shrink-0 rounded-[9px]" />
          {!collapsed ? (
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold tracking-tight">Notify</span>
              <span className="block truncate text-[11px] text-muted-foreground">Delivery platform</span>
            </span>
          ) : null}
        </Link>
      </div>

      <nav aria-label="Primary" className="flex-1 overflow-y-auto px-2 py-2">
        {sections.map((section) => (
          <div key={section.title} className="mb-4">
            {!collapsed ? (
              <p className="px-2.5 pb-1.5 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                {section.title}
              </p>
            ) : (
              <div className="mx-2 mb-2 h-px bg-border" aria-hidden="true" />
            )}
            <ul className="space-y-0.5">
              {section.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    title={collapsed ? item.label : undefined}
                    className={({ isActive }) =>
                      cn(
                        'group relative flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm font-medium transition-colors duration-150',
                        'hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
                        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                        isActive
                          ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                          : 'text-sidebar-foreground/80',
                        collapsed && 'justify-center px-0',
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <span
                          aria-hidden="true"
                          className={cn(
                            'absolute left-0 top-1/2 h-4 w-0.5 -translate-y-1/2 rounded-r-full bg-primary transition-opacity',
                            isActive ? 'opacity-100' : 'opacity-0',
                          )}
                        />
                        <item.icon className="size-4 shrink-0" aria-hidden="true" />
                        {!collapsed ? <span className="truncate">{item.label}</span> : null}
                        {!collapsed && item.to === '/inbox' && unread > 0 ? (
                          <span className="ml-auto rounded-full bg-primary px-1.5 py-0.5 text-[10px] font-semibold text-primary-foreground tabular">
                            {unread > 99 ? '99+' : unread}
                          </span>
                        ) : null}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-sidebar-border p-2">
        <Button
          variant="ghost"
          size={collapsed ? 'icon' : 'sm'}
          className={cn('w-full text-muted-foreground', collapsed && 'px-0')}
          onClick={() => setCollapsed((value) => !value)}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-expanded={!collapsed}
        >
          {collapsed ? <PanelLeftOpen aria-hidden="true" /> : <PanelLeftClose aria-hidden="true" />}
          {!collapsed ? <span>Collapse</span> : null}
        </Button>
      </div>
    </div>
  );

  return (
    <div className="min-h-dvh bg-background">
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>

      {/* ---------- Desktop sidebar ---------- */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-30 hidden border-r border-sidebar-border bg-sidebar transition-[width] duration-200 ease-out-quart lg:block',
          collapsed ? 'w-16' : 'w-64',
        )}
      >
        {sidebar}
      </aside>

      {/* ---------- Mobile slide-over ---------- */}
      {mobileOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            className="absolute inset-0 animate-in bg-black/55 backdrop-blur-[2px] fade-in-0"
            onClick={() => setMobileOpen(false)}
          />
          <aside
            ref={mobilePanelRef}
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="absolute inset-y-0 left-0 w-[17rem] max-w-[85vw] animate-in border-r border-sidebar-border bg-sidebar shadow-xl slide-in-from-left duration-200"
          >
            <Button
              variant="ghost"
              size="icon-sm"
              className="absolute right-2 top-2.5 z-10"
              onClick={() => setMobileOpen(false)}
              aria-label="Close navigation"
            >
              <X aria-hidden="true" />
            </Button>
            {sidebar}
          </aside>
        </div>
      ) : null}

      <div className={cn('transition-[padding] duration-200 ease-out-quart', collapsed ? 'lg:pl-16' : 'lg:pl-64')}>
        {/* ---------- Topbar ---------- */}
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-border bg-background/85 px-4 backdrop-blur-md sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setMobileOpen(true)}
            aria-label="Open navigation"
            aria-expanded={mobileOpen}
          >
            <Menu aria-hidden="true" />
          </Button>

          {/* Global search trigger / command palette */}
          <button
            type="button"
            onClick={openPalette}
            className="group flex h-9 min-w-0 flex-1 items-center gap-2 rounded-md border border-input bg-background px-3 text-sm text-muted-foreground shadow-sm transition-colors hover:bg-accent/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:max-w-md"
            aria-label="Open command palette"
            aria-keyshortcuts="Meta+K Control+K"
          >
            <Search className="size-4 shrink-0" aria-hidden="true" />
            <span className="truncate">Search or jump to…</span>
            <kbd className="ml-auto hidden shrink-0 rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[10px] sm:inline-block">
              ⌘K
            </kbd>
          </button>

          <div className="ml-auto flex items-center gap-0.5">
            {/* Live connection + unread state. The connection state is spelled out in
                the tooltip and aria-label — the dot is never the only signal (§6). */}
            <AppTooltip
              label={
                connected
                  ? 'Live updates connected'
                  : 'Live updates disconnected — reconnecting'
              }
            >
              <Button
                variant="ghost"
                size="icon"
                className="relative"
                onClick={() => setDrawerOpen(true)}
                aria-label={
                  unread > 0
                    ? `Notifications, ${unread} unread. Live updates ${connected ? 'connected' : 'disconnected'}.`
                    : `Notifications. Live updates ${connected ? 'connected' : 'disconnected'}.`
                }
              >
                <Bell aria-hidden="true" />
                {unread > 0 ? (
                  <span className="absolute -right-0.5 -top-0.5 flex min-w-[1.15rem] items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground tabular">
                    {unread > 99 ? '99+' : unread}
                  </span>
                ) : null}
                <span
                  aria-hidden="true"
                  className={cn(
                    'absolute bottom-1.5 right-1.5 size-1.5 rounded-full ring-2 ring-background',
                    connected ? 'bg-success' : 'bg-muted-foreground',
                  )}
                />
              </Button>
            </AppTooltip>

            <AppTooltip
              label={
                preference === 'light'
                  ? 'Theme: light'
                  : preference === 'dark'
                    ? 'Theme: dark'
                    : 'Theme: system'
              }
            >
              <Button
                variant="ghost"
                size="icon"
                onClick={toggle}
                aria-label={`Switch theme (currently ${preference})`}
              >
                {preference === 'light' ? (
                  <Sun aria-hidden="true" />
                ) : preference === 'dark' ? (
                  <Moon aria-hidden="true" />
                ) : (
                  <Monitor aria-hidden="true" />
                )}
              </Button>
            </AppTooltip>

            {/* User menu */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="ml-1 flex items-center gap-2 rounded-md p-1 pr-2 transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  aria-label="Account menu"
                >
                  <UserAvatar name={user?.displayName} email={user?.email} className="size-7" />
                  <span className="hidden max-w-[9rem] truncate text-sm font-medium sm:inline">
                    {user?.displayName || user?.email}
                  </span>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-60">
                <DropdownMenuLabel className="space-y-1 py-2">
                  <span className="block truncate text-sm font-medium text-foreground">
                    {user?.displayName || 'Account'}
                  </span>
                  <span className="block truncate text-xs font-normal text-muted-foreground">
                    {user?.email}
                  </span>
                  <span className="flex flex-wrap gap-1 pt-1">
                    {(user?.roles ?? []).map((role) => (
                      <Badge key={role} variant={role === 'ADMIN' ? 'default' : 'muted'} className="text-[10px]">
                        {role}
                      </Badge>
                    ))}
                  </span>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => navigate('/settings')}>
                  <Settings aria-hidden="true" />
                  Settings
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => navigate('/preferences')}>
                  <SlidersHorizontal aria-hidden="true" />
                  Delivery preferences
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => navigate('/status')}>
                  <Activity aria-hidden="true" />
                  System status
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => setTheme('system')}>
                  <Monitor aria-hidden="true" />
                  Theme: system
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => setTheme(preference === 'dark' ? 'light' : 'dark')}
                >
                  {preference === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
                  Theme: {preference === 'dark' ? 'light' : 'dark'}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  variant="destructive"
                  onSelect={() => {
                    logout();
                    navigate('/login');
                  }}
                >
                  <LogOut aria-hidden="true" />
                  Log out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>

        <main id="main-content" tabIndex={-1} className="px-4 py-6 outline-none sm:px-6 lg:px-8">
          {children ?? <Outlet />}
        </main>
      </div>

      <CommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        unread={unread}
        sections={sections}
      />
      <NotificationDrawer open={drawerOpen} onOpenChange={setDrawerOpen} />
    </div>
  );
}
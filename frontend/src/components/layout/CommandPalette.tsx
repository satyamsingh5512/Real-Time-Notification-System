import { useMemo } from 'react';
import { Command } from 'cmdk';
import {
  Bell,
  LogOut,
  Monitor,
  Moon,
  Search,
  Sun,
  type LucideIcon,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/stores/auth';
import { useTheme } from '@/stores/theme';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import type { NAV_SECTIONS } from './AppShell';

type NavSection = (typeof NAV_SECTIONS)[number];

interface PaletteCommand {
  id: string;
  label: string;
  /** Extra keywords cmdk matches on, so "unread" finds the inbox. */
  keywords: string;
  group: string;
  icon: LucideIcon;
  shortcut?: string;
  run: () => void;
}

/**
 * Command palette (spec §17).
 *
 * ⌘K / Ctrl+K opens it (bound in AppShell); Escape closes it; arrow keys move.
 *
 * Navigation commands are *derived from the sidebar nav model* rather than hand-listed.
 * They used to be a second hard-coded list, which is how every palette link drifted out
 * of sync with the routes. There is now exactly one source of truth for destinations.
 *
 * Permission filtering happens in AppShell before the sections reach this component, so
 * admin-only entries never appear for other roles — and RBAC is still enforced
 * server-side regardless.
 */
export function CommandPalette({
  open,
  onOpenChange,
  unread,
  sections,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  unread: number;
  sections: NavSection[];
}) {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const { preference, setTheme } = useTheme();

  const commands = useMemo<PaletteCommand[]>(() => {
    const go = (path: string) => () => {
      onOpenChange(false);
      navigate(path);
    };

    const navigation: PaletteCommand[] = sections.flatMap((section) =>
      section.items.map((item) => ({
        id: `nav:${item.to}`,
        // Surface the live unread count on the inbox entry so the palette and the
        // sidebar badge never disagree.
        label:
          item.to === '/inbox' && unread > 0
            ? `${item.label} (${unread} unread)`
            : item.label,
        keywords: `${item.label} ${item.description} ${section.title}`,
        group: 'Navigate',
        icon: item.icon,
        run: go(item.to),
      })),
    );

    const preferences: PaletteCommand[] = (
      [
        { value: 'light' as const, label: 'Theme: light', icon: Sun },
        { value: 'dark' as const, label: 'Theme: dark', icon: Moon },
        { value: 'system' as const, label: 'Theme: system', icon: Monitor },
      ]
    ).map((option) => ({
      id: `theme:${option.value}`,
      label: `${option.label}${preference === option.value ? ' (active)' : ''}`,
      keywords: `theme appearance ${option.value} ${option.label}`,
      group: 'Preferences',
      icon: option.icon,
      run: () => {
        setTheme(option.value);
        onOpenChange(false);
      },
    }));

    const account: PaletteCommand[] = [
      {
        id: 'notifications',
        label: unread > 0 ? `Notification drawer (${unread} unread)` : 'Notification drawer',
        keywords: 'bell drawer alerts unread inbox',
        group: 'Account',
        icon: Bell,
        shortcut: 'N',
        run: () => {
          onOpenChange(false);
          window.dispatchEvent(new CustomEvent('rtns:open-notifications'));
        },
      },
      {
        id: 'logout',
        label: 'Log out',
        keywords: 'sign out exit session',
        group: 'Account',
        icon: LogOut,
        run: () => {
          onOpenChange(false);
          logout();
          navigate('/login');
        },
      },
    ];

    return [...navigation, ...preferences, ...account];
  }, [sections, unread, navigate, onOpenChange, logout, preference, setTheme]);

  const groups = useMemo(
    () => ['Navigate', 'Preferences', 'Account'],
    [],
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        hideClose
        className="top-[18%] max-w-xl translate-y-0 gap-0 overflow-hidden p-0 sm:top-[22%]"
      >
        <DialogTitle className="sr-only">Command palette</DialogTitle>
        <Command
          loop
          className="flex max-h-[24rem] w-full flex-col overflow-hidden rounded-xl"
          filter={(value, search) => {
            const needle = search.toLowerCase();
            const haystack = value.toLowerCase();
            if (haystack.includes(needle)) return 1;
            if (haystack.startsWith(needle)) return 0.5;
            return 0;
          }}
        >
          <div className="flex items-center gap-2.5 border-b border-border px-4">
            <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <Command.Input
              autoFocus
              placeholder="Type a command or search…"
              aria-label="Search commands"
              className="h-12 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
            <kbd className="hidden shrink-0 rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground sm:block">
              ESC
            </kbd>
          </div>
          <Command.List className="max-h-[20rem] overflow-y-auto p-2">
            <Command.Empty className="py-8 text-center text-sm text-muted-foreground">
              No matching commands.
            </Command.Empty>
            {groups.map((group) => {
              const items = commands.filter((command) => command.group === group);
              if (items.length === 0) return null;
              return (
                <Command.Group key={group} heading={group} className="mb-1 last:mb-0">
                  {items.map((command) => (
                    <Command.Item
                      key={command.id}
                      value={`${command.label} ${command.keywords}`}
                      onSelect={command.run}
                      className="flex cursor-pointer select-none items-center gap-2.5 rounded-md px-2.5 py-2 text-sm text-foreground outline-none data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground"
                    >
                      <command.icon className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                      <span className="min-w-0 flex-1 truncate">{command.label}</span>
                      {command.shortcut ? (
                        <kbd className="shrink-0 rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
                          {command.shortcut}
                        </kbd>
                      ) : null}
                    </Command.Item>
                  ))}
                </Command.Group>
              );
            })}
          </Command.List>
        </Command>
      </DialogContent>
    </Dialog>
  );
}
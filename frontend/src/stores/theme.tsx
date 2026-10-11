import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

const STORAGE_KEY = 'rtns.theme-preference';

export type ThemePreference = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

/**
 * The inline script in index.html has already resolved and applied the stored theme
 * before React mounts — that is what prevents the wrong-theme flash on first paint
 * (spec §27). This provider only mirrors the applied value back into React state so
 * every consumer (shell, toaster, command palette) reacts to one shared source.
 */
function readAppliedTheme(): ResolvedTheme {
  if (typeof document === 'undefined') return 'light';
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
}

function readPreference(): ThemePreference {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === 'light' || raw === 'dark' || raw === 'system') return raw;
  } catch {
    /* storage unavailable — fall through to the default */
  }
  return 'system';
}

function resolve(preference: ThemePreference): ResolvedTheme {
  if (preference !== 'system') return preference;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/** Applies the class to <html> and persists the user's explicit choice. */
function apply(preference: ThemePreference): void {
  const resolved = resolve(preference);
  const root = document.documentElement;
  root.classList.toggle('dark', resolved === 'dark');
  // `data-theme` mirrors the resolved value so CSS that can't rely on the `.dark`
  // ancestor (e.g. the shader contrast scrim, print styles) observes the same theme.
  root.dataset.theme = resolved;
  root.style.colorScheme = resolved;
  try {
    localStorage.setItem(STORAGE_KEY, preference);
  } catch {
    /* ignore — preference is simply not persisted */
  }
}

interface ThemeContextValue {
  /** The preference the user chose — may be `system`. */
  preference: ThemePreference;
  /** What is actually painted right now — never `system`. */
  theme: ResolvedTheme;
  setTheme: (preference: ThemePreference) => void;
  /** Flips light↔dark. Explicitly leaves `system` so the toggle never lies about state. */
  toggle: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Three-state theme control (light / dark / system) with a single shared store.
 *
 * This is a context rather than a hook because the toaster needs to follow the theme
 * too: two independent `useTheme()` instances meant `Sonner` kept rendering with the
 * theme that was active at mount, so a toggle never visibly updated toasts.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreference] = useState<ThemePreference>(readPreference);
  const [theme, setThemeState] = useState<ResolvedTheme>(readAppliedTheme);

  useEffect(() => {
    apply(preference);
    setThemeState(readAppliedTheme());

    // `system` keeps tracking the OS live, so switching the OS theme updates the app.
    if (preference !== 'system') return;
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => {
      apply('system');
      setThemeState(readAppliedTheme());
    };
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [preference]);

  // Keep duplicate tabs of the app in sync when the preference changes elsewhere.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY) return;
      setPreference(readPreference());
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const setTheme = useCallback((next: ThemePreference) => setPreference(next), []);
  const toggle = useCallback(() => {
    setPreference(readAppliedTheme() === 'dark' ? 'light' : 'dark');
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ preference, theme, setTheme, toggle }),
    [preference, theme, setTheme, toggle],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}
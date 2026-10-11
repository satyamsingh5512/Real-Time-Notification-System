import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { authApi, notificationsApi } from '@/services/api';
import { ApiError, TOKEN_STORAGE_KEY, USER_STORAGE_KEY, onUnauthorized } from '@/services/api-client';
import type { AuthResponse } from '@/types/api';

interface AuthContextValue {
  user: AuthResponse | null;
  /** True while the initial session check is in flight (avoids a login flash). */
  checking: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readStoredUser(): AuthResponse | null {
  try {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    if (!token || !raw) return null;
    return JSON.parse(raw) as AuthResponse;
  } catch {
    return null;
  }
}

function persist(auth: AuthResponse) {
  localStorage.setItem(TOKEN_STORAGE_KEY, auth.token);
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(auth));
}

function clearStored() {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(USER_STORAGE_KEY);
}

/**
 * Single auth context (spec §32). The backend issues a short-lived stateless JWT with no
 * refresh flow, so the authoritative check on boot is a cheap authenticated request:
 * if it 401s, `api-client` clears storage and calls the handler registered here, which
 * drops the session and sends the user to /login.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthResponse | null>(readStoredUser);
  const [checking, setChecking] = useState<boolean>(() => Boolean(readStoredUser()));

  const logout = useCallback(() => {
    clearStored();
    setUser(null);
  }, []);

  // A 401 anywhere in the app tears the session down exactly once, centrally.
  useEffect(() => {
    onUnauthorized(() => setUser(null));
    return () => onUnauthorized(null);
  }, []);

  // Validate a restored session on boot so a stale token doesn't render a broken shell.
  useEffect(() => {
    let cancelled = false;
    const stored = readStoredUser();
    if (!stored) {
      setChecking(false);
      return;
    }
    (async () => {
      try {
        await notificationsApi.unreadCount();
      } catch (caught) {
        if (cancelled) return;
        // Only an actual authentication failure invalidates the session. A 503, a
        // timeout or an offline network must NOT sign the user out — the stored token
        // may still be perfectly valid and the user would lose their work on a blip.
        // Such failures fall through to the page's own error state, which offers a
        // retry, and the central 401 handler (`onUnauthorized`) clears the session when
        // the token really is rejected.
        if (caught instanceof ApiError && (caught.status === 401 || caught.kind === 'forbidden')) {
          clearStored();
          setUser(null);
        }
      } finally {
        if (!cancelled) setChecking(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await authApi.login(email, password);
    persist(auth);
    setUser(auth);
  }, []);

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const auth = await authApi.register(email, password, displayName);
    persist(auth);
    setUser(auth);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      checking,
      isAuthenticated: Boolean(user),
      isAdmin: user?.roles.includes('ADMIN') ?? false,
      login,
      register,
      logout,
    }),
    [user, checking, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
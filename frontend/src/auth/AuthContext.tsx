import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, type AuthResponse } from '../api/client';

interface AuthState {
  user: AuthResponse | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

function loadStored(): AuthResponse | null {
  try {
    const raw = localStorage.getItem('rtns.user');
    return raw ? (JSON.parse(raw) as AuthResponse) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthResponse | null>(loadStored);

  const persist = useCallback((auth: AuthResponse) => {
    localStorage.setItem('rtns.token', auth.token);
    localStorage.setItem('rtns.user', JSON.stringify(auth));
    setUser(auth);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      persist(await api.login(email, password));
    },
    [persist],
  );

  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      persist(await api.register(email, password, displayName));
    },
    [persist],
  );

  const logout = useCallback(() => {
    localStorage.removeItem('rtns.token');
    localStorage.removeItem('rtns.user');
    setUser(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      login,
      register,
      logout,
      isAdmin: user?.roles.includes('ADMIN') ?? false,
    }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

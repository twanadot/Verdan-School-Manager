import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { login as loginApi } from '../api/auth';
import { setTokens, clearTokens, getToken } from '../api/client';
import type { UserInfo, LoginRequest } from '../types';

interface AuthContextType {
  user: UserInfo | null;
  isAuthenticated: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

/** Decode JWT payload without verification (client-side only) */
function decodeToken(token: string): UserInfo | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    // Try to get full user from localStorage first (has more fields)
    const stored = localStorage.getItem('verdan_user');
    if (stored) {
      try {
        return JSON.parse(stored);
      } catch {
        // Stored data corrupted, fall through to JWT decode
      }
    }
    return {
      id: parseInt(payload.sub),
      username: payload.username,
      role: payload.role,
      firstName: '',
      lastName: '',
      email: '',
      institutionId: payload.institutionId,
      institutionName: payload.institutionName,
      institutionLevel: payload.institutionLevel,
    };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(() => {
    const token = getToken();
    if (!token) return null;
    const decoded = decodeToken(token);
    if (!decoded) {
      clearTokens();
      localStorage.removeItem('verdan_user');
      return null;
    }
    return decoded;
  });

  const login = useCallback(async (credentials: LoginRequest) => {
    const response = await loginApi(credentials);
    setTokens(response.token, response.refreshToken);
    setUser(response.user);
    localStorage.setItem('verdan_user', JSON.stringify(response.user));
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    setUser(null);
    localStorage.removeItem('verdan_user');
    // Clear all React Query caches so stale data from previous user doesn't persist
    window.location.href = '/login';
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

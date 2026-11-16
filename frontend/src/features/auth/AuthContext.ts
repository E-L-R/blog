/**
 * Who is logged in, for any component: `useAuth()`. Part of the `auth` feature's public API.
 */
import { createContext, useContext } from 'react';
import type { Me } from '@/api/types';

export type AuthState =
  /** No token. */
  | { status: 'anonymous'; me: null }
  /** A token, and its account is being loaded. */
  | { status: 'loading'; me: null }
  | { status: 'authenticated'; me: Me };

export interface AuthContextValue {
  state: AuthState;
  /** Stores the token from a successful login. */
  logIn(accessToken: string, expiresIn: number): void;
  /**
   * Forgets the token. The API has no logout endpoint, so the token itself stays valid until
   * it expires (SECURITY.md#known-limitations).
   */
  logOut(): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (value === null) throw new Error('useAuth must be used inside <AuthProvider>.');
  return value;
}

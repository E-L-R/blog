/**
 * Provides the login state to the app. Part of the `auth` feature's public API.
 *
 * The token comes from the shared token store; this component loads the account it belongs to
 * (`GET /me`), and logs the user out when the token expires.
 */
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { tokenStore } from '@/api/token';
import { fetchMe } from './api';
import { AuthContext, type AuthContextValue, type AuthState } from './AuthContext';

const meQueryKey = ['me'] as const;

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const token = useSyncExternalStore(tokenStore.subscribe, tokenStore.get);

  const me = useQuery({
    queryKey: meQueryKey,
    queryFn: ({ signal }) => fetchMe(signal),
    enabled: token !== null,
    staleTime: Infinity,
  });

  useEffect(() => {
    if (token === null) {
      // Another account may log in next; it must not see this one's data.
      queryClient.removeQueries({ queryKey: meQueryKey });
      return;
    }
    const expiresAt = tokenStore.expiresAt();
    if (expiresAt === null) return;
    const timer = setTimeout(() => tokenStore.clear(), Math.max(0, expiresAt - Date.now()));
    return () => clearTimeout(timer);
  }, [token, queryClient]);

  const value = useMemo<AuthContextValue>(() => {
    let state: AuthState;
    if (token === null) state = { status: 'anonymous', me: null };
    else if (me.data !== undefined) state = { status: 'authenticated', me: me.data };
    else if (me.isError) state = { status: 'anonymous', me: null };
    else state = { status: 'loading', me: null };

    return {
      state,
      logIn(accessToken, expiresIn) {
        queryClient.removeQueries({ queryKey: meQueryKey });
        tokenStore.set(accessToken, expiresIn);
      },
      logOut() {
        tokenStore.clear();
      },
    };
  }, [token, me.data, me.isError, queryClient]);

  return <AuthContext value={value}>{children}</AuthContext>;
}

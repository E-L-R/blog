/**
 * A layout route for pages that need a logged-in user. Anyone else is sent to the login page,
 * which brings them back here afterwards. Part of the `auth` feature's public API.
 */
import { Navigate, Outlet, useLocation } from 'react-router';
import { Loading } from '@/components/States';
import { useAuth } from './AuthContext';

export function RequireAuth() {
  const { state } = useAuth();
  const location = useLocation();

  if (state.status === 'loading') return <Loading />;
  if (state.status === 'anonymous') {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  return <Outlet />;
}

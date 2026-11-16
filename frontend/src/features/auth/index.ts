/**
 * The `auth` feature's public API: logging in, registering, and who the current user is.
 * Other features and the app import only from here (docs/FRONTEND.md#features).
 */
export { AuthProvider } from './AuthProvider';
export { useAuth, type AuthState } from './AuthContext';
export { RequireAuth } from './RequireAuth';
export { LoginPage } from './LoginPage';
export { RegisterPage } from './RegisterPage';

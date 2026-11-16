/**
 * Where to go after logging in. Only paths inside this app are accepted: a link such as
 * `/login?redirect=https://evil.example` must not send the user to another site after they
 * enter their password (an "open redirect").
 */
export function safeRedirect(value: string | null): string {
  if (value === null || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return '/';
  }
  return value;
}

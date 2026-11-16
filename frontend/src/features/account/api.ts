/**
 * The `account` feature's API calls (docs/API.md#users).
 */
import { apiRequest } from '@/api/client';
import type { Id, User } from '@/api/types';

/** `GET /users/{id}`: a public profile. `404` for a deleted account. */
export function getUser(id: Id, signal?: AbortSignal): Promise<User> {
  return apiRequest<User>(`/users/${encodeURIComponent(id)}`, { signal });
}

/** `POST /me/delete`: deletes the current user's account. Needs the password. */
export function deleteAccount(password: string): Promise<void> {
  return apiRequest<void>('/me/delete', { method: 'POST', body: { password } });
}

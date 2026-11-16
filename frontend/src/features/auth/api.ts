/**
 * The `auth` feature's API calls (docs/API.md#auth and #users).
 */
import { apiRequest } from '@/api/client';
import type { LoginResponse, Me } from '@/api/types';

export interface RegisterInput {
  email: string;
  username: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

/** `POST /auth/register`. Registering does not log the user in. */
export function register(input: RegisterInput): Promise<Me> {
  return apiRequest<Me>('/auth/register', { method: 'POST', body: input });
}

/** `POST /auth/login`. */
export function login(input: LoginInput): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/auth/login', { method: 'POST', body: input });
}

/** `GET /me`: the account the token belongs to. */
export function fetchMe(signal?: AbortSignal): Promise<Me> {
  return apiRequest<Me>('/me', { signal });
}

/**
 * The one place that talks HTTP to the backend. Feature `api.ts` files call {@link apiRequest}
 * and get typed data back, or an {@link ApiError}.
 */
import { tokenStore } from './token';
import type { FieldError, ProblemDetail } from './types';

/**
 * The path the app calls the API under. In development the Vite dev server forwards it to the
 * backend without the prefix (vite.config.ts); in production a reverse proxy does the same
 * (docs/OPERATIONS.md#frontend).
 */
export const API_BASE_PATH = '/api';

/**
 * A failed API call. `code` is the API's stable error code (docs/API.md#errors), plus two the
 * client adds: `NETWORK_ERROR` when no response arrived, and `UNKNOWN` for a response that is
 * not Problem Details.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  /** Sent as JSON. */
  body?: unknown;
  /** Added to the URL; `undefined` values are left out. */
  query?: Record<string, string | number | undefined>;
  signal?: AbortSignal;
}

/**
 * Calls the API and returns the parsed JSON body, or `undefined` for an empty response
 * (`204`). Throws {@link ApiError} for any status that is not 2xx.
 *
 * The token, if there is one, is sent with every request. The API refuses a token that no
 * longer works (expired, or its account deleted) with `401`, even on public endpoints. So a
 * `401` to a request that carried a token clears the token, which logs the user out, and a
 * `GET` is sent once more without it: a public page still loads.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = tokenStore.get();
  try {
    return await send<T>(path, options, token);
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401 && token !== null)) throw error;
    tokenStore.clear();
    if ((options.method ?? 'GET') !== 'GET') throw error;
    return send<T>(path, options, null);
  }
}

async function send<T>(path: string, options: RequestOptions, token: string | null): Promise<T> {
  const { method = 'GET', body, query, signal } = options;
  const url = new URL(API_BASE_PATH + path, window.location.origin);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }

  const headers = new Headers({ Accept: 'application/json, application/problem+json' });
  if (body !== undefined) headers.set('Content-Type', 'application/json');
  if (token !== null) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError(0, 'NETWORK_ERROR', 'The server could not be reached.');
  }

  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text === '' ? undefined : JSON.parse(text)) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  const problem = await readProblem(response);
  if (problem !== null) {
    return new ApiError(
      response.status,
      problem.code,
      problem.detail ?? problem.title ?? response.statusText,
      problem.errors ?? [],
    );
  }
  // The backend sends Problem Details, but a proxy in front of it may answer 401 without a body.
  const code = response.status === 401 ? 'UNAUTHENTICATED' : 'UNKNOWN';
  return new ApiError(response.status, code, response.statusText || `HTTP ${response.status}`);
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    const body: unknown = JSON.parse(await response.text());
    if (
      typeof body === 'object' &&
      body !== null &&
      typeof (body as ProblemDetail).code === 'string'
    ) {
      return body as ProblemDetail;
    }
  } catch {
    // Not JSON: fall through.
  }
  return null;
}

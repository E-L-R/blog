/**
 * Turns an {@link ApiError} into text for the user. The app branches on the error's `code`,
 * never on the server's wording, which docs/API.md#errors says may change.
 */
import { ApiError } from '@/api/client';
import type { FieldErrors } from './validation';

const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: "Can't reach the server. Check your connection and try again.",
  UNAUTHENTICATED: 'Your session has ended. Log in again.',
  INVALID_CREDENTIALS: 'Wrong email or password.',
  INVALID_PASSWORD: 'Wrong password.',
  NOT_AUTHOR: 'Only the author can do that.',
  NOT_FOUND: "This doesn't exist, or it was deleted.",
  EMAIL_TAKEN: 'An account with this email already exists.',
  USERNAME_TAKEN: 'This username is taken. Usernames of deleted accounts stay taken too.',
  EDIT_CONFLICT: 'Someone saved this at the same moment. Reload the page and try again.',
  TARGET_DELETED: 'This comment was deleted in the meantime.',
  VALIDATION_FAILED: 'Some fields need another look.',
};

const FALLBACK = 'Something went wrong. Please try again.';

/** A message for any error a query or mutation can throw. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return MESSAGES[error.code] ?? FALLBACK;
  return FALLBACK;
}

/**
 * The server's field errors from a `VALIDATION_FAILED` response, keyed by field, for the
 * fields a form shows. A field that breaks several rules shows the first.
 */
export function serverFieldErrors<F extends string>(
  error: unknown,
  fields: readonly F[],
): FieldErrors<F> {
  const result: FieldErrors<F> = {};
  if (!(error instanceof ApiError)) return result;
  for (const { field, message } of error.fieldErrors) {
    if ((fields as readonly string[]).includes(field) && result[field as F] === undefined) {
      result[field as F] = capitalize(message);
    }
  }
  return result;
}

/**
 * The field that a conflict code is about, so the form can show the message next to it.
 */
export function conflictField(error: unknown): 'email' | 'username' | null {
  if (!(error instanceof ApiError)) return null;
  if (error.code === 'EMAIL_TAKEN') return 'email';
  if (error.code === 'USERNAME_TAKEN') return 'username';
  return null;
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1) + (text.endsWith('.') ? '' : '.');
}

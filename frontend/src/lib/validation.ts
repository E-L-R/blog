/**
 * The input rules from docs/API.md#validation, checked in the browser so the user sees a
 * problem before submitting. The server checks them again and stays the authority: these
 * only save a round trip.
 *
 * Each function returns an error message, or `null` if the value is valid.
 */

export const LIMITS = {
  emailMax: 254,
  usernameMin: 3,
  usernameMax: 30,
  passwordMin: 8,
  passwordMaxBytes: 72,
  titleMax: 200,
  postContentMax: 50_000,
  commentContentMax: 10_000,
} as const;

// Deliberately loose: the server's @Email check decides. This only catches obvious typos.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;
const USERNAME_PATTERN = /^[A-Za-z0-9_-]+$/;

export function validateEmail(email: string): string | null {
  if (email.trim() === '') return 'Enter your email address.';
  if (!EMAIL_PATTERN.test(email)) return 'Enter a valid email address.';
  if (email.length > LIMITS.emailMax) return `Use at most ${LIMITS.emailMax} characters.`;
  return null;
}

export function validateUsername(username: string): string | null {
  if (username.length < LIMITS.usernameMin || username.length > LIMITS.usernameMax) {
    return `Use ${LIMITS.usernameMin} to ${LIMITS.usernameMax} characters.`;
  }
  if (!USERNAME_PATTERN.test(username)) return 'Use only letters, digits, _ and -.';
  return null;
}

/** For a new password. Logging in only checks that one was entered. */
export function validateNewPassword(password: string): string | null {
  if (password.length < LIMITS.passwordMin) {
    return `Use at least ${LIMITS.passwordMin} characters.`;
  }
  // bcrypt only reads the first 72 bytes, so the API refuses longer passwords. Letters
  // outside ASCII take 2 to 4 bytes each, so this counts bytes, not characters.
  if (utf8Length(password) > LIMITS.passwordMaxBytes) return 'This password is too long.';
  return null;
}

export function validateRequired(value: string, what: string): string | null {
  return value.trim() === '' ? `Enter ${what}.` : null;
}

export function validateTitle(title: string): string | null {
  if (title.trim() === '') return 'Give your post a title.';
  // `length` counts UTF-16 code units, exactly as Java's @Size on the server does.
  if (title.length > LIMITS.titleMax) return `Use at most ${LIMITS.titleMax} characters.`;
  return null;
}

export function validatePostContent(content: string): string | null {
  if (content.trim() === '') return 'Write something first.';
  if (content.length > LIMITS.postContentMax) {
    return `Use at most ${LIMITS.postContentMax.toLocaleString('en')} characters.`;
  }
  return null;
}

export function validateCommentContent(content: string): string | null {
  if (content.trim() === '') return 'Write something first.';
  if (content.length > LIMITS.commentContentMax) {
    return `Use at most ${LIMITS.commentContentMax.toLocaleString('en')} characters.`;
  }
  return null;
}

export function utf8Length(text: string): number {
  return new TextEncoder().encode(text).length;
}

/** The errors of a form, by field name. Fields without an error are left out. */
export type FieldErrors<F extends string> = Partial<Record<F, string>>;

/** Runs each field's check and keeps the failures. */
export function collectErrors<F extends string>(checks: Record<F, string | null>): FieldErrors<F> {
  const errors: FieldErrors<F> = {};
  for (const field of Object.keys(checks) as F[]) {
    const message = checks[field];
    if (message !== null) errors[field] = message;
  }
  return errors;
}

export function hasErrors(errors: object): boolean {
  return Object.keys(errors).length > 0;
}

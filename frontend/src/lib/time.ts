/**
 * Formats the API's timestamps for people. Every function takes the current time as an
 * argument instead of reading the clock, so tests get the same output every time.
 */

const RELATIVE = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
const DATE = new Intl.DateTimeFormat('en', { day: 'numeric', month: 'short', year: 'numeric' });
const DATE_TIME = new Intl.DateTimeFormat('en', { dateStyle: 'long', timeStyle: 'short' });

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "just now", "5 minutes ago", "yesterday", "3 days ago"; after a week, the date. */
export function formatRelative(timestamp: string, now: number): string {
  const then = Date.parse(timestamp);
  const elapsed = now - then;
  if (elapsed < MINUTE) return 'just now';
  if (elapsed < HOUR) return RELATIVE.format(-Math.floor(elapsed / MINUTE), 'minute');
  if (elapsed < DAY) return RELATIVE.format(-Math.floor(elapsed / HOUR), 'hour');
  if (elapsed < 7 * DAY) return RELATIVE.format(-Math.floor(elapsed / DAY), 'day');
  return DATE.format(then);
}

/** "5 November 2026 at 12:00", for tooltips. */
export function formatDateTime(timestamp: string): string {
  return DATE_TIME.format(Date.parse(timestamp));
}

/** "Nov 5, 2026". */
export function formatDate(timestamp: string): string {
  return DATE.format(Date.parse(timestamp));
}

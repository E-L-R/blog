import { describe, expect, it } from 'vitest';
import { formatDate, formatRelative } from './time';

describe('formatRelative', () => {
  const now = Date.parse('2026-11-16T12:00:00Z');
  const ago = (ms: number) => new Date(now - ms).toISOString();

  it.each([
    [10_000, 'just now'],
    [5 * 60_000, '5 minutes ago'],
    [3 * 3_600_000, '3 hours ago'],
    [26 * 3_600_000, 'yesterday'],
    [3 * 86_400_000, '3 days ago'],
  ])('shows %i ms ago as "%s"', (elapsed, expected) => {
    expect(formatRelative(ago(elapsed), now)).toBe(expected);
  });

  it('shows the date after a week', () => {
    expect(formatRelative('2026-11-01T12:00:00Z', now)).toBe(formatDate('2026-11-01T12:00:00Z'));
  });
});

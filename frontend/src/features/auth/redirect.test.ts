import { describe, expect, it } from 'vitest';
import { safeRedirect } from './redirect';

describe('safeRedirect', () => {
  it('keeps paths inside the app', () => {
    expect(safeRedirect('/posts/new')).toBe('/posts/new');
    expect(safeRedirect('/?page=2')).toBe('/?page=2');
  });

  it.each([
    null,
    '',
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
  ])('refuses %s and goes home instead', (value) => {
    expect(safeRedirect(value)).toBe('/');
  });
});

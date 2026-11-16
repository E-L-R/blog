import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createTokenStore } from './token';

describe('token store', () => {
  let now: number;
  const store = () => createTokenStore(localStorage, () => now);

  beforeEach(() => {
    now = Date.parse('2026-11-05T12:00:00Z');
  });

  it('returns the stored token until it expires', () => {
    const tokens = store();
    tokens.set('abc', 60);

    expect(tokens.get()).toBe('abc');
    now += 59_999;
    expect(tokens.get()).toBe('abc');
    now += 1;
    expect(tokens.get()).toBeNull();
  });

  it('keeps the token across instances, as after a page reload', () => {
    store().set('abc', 60);

    expect(store().get()).toBe('abc');
  });

  it('forgets the token when cleared', () => {
    const tokens = store();
    tokens.set('abc', 60);
    tokens.clear();

    expect(tokens.get()).toBeNull();
    expect(tokens.expiresAt()).toBeNull();
  });

  it('treats a corrupt entry as no token', () => {
    localStorage.setItem('blog.auth', '{not json');

    expect(store().get()).toBeNull();
  });

  it('tells subscribers when the token changes, until they unsubscribe', () => {
    const tokens = store();
    const listener = vi.fn();
    const unsubscribe = tokens.subscribe(listener);

    tokens.set('abc', 60);
    tokens.clear();
    unsubscribe();
    tokens.set('def', 60);

    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('tells subscribers when another tab changes the token', () => {
    const listener = vi.fn();
    store().subscribe(listener);

    window.dispatchEvent(new StorageEvent('storage', { key: 'blog.auth' }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'something.else' }));

    expect(listener).toHaveBeenCalledTimes(1);
  });
});

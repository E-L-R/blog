/**
 * Runs before every test file: adds the DOM matchers, starts the mock API, and gives every
 * test a clean slate (no token, a fresh database, an empty DOM).
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { resetMockApi, server } from '@/mocks/server';

// A request with no handler is a bug in the test or the app, so it fails the test.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
beforeEach(() => {
  localStorage.clear();
  resetMockApi();
});
afterEach(() => {
  cleanup();
  server.events.removeAllListeners();
});

// jsdom has no layout, so it can't scroll. React Router's <ScrollRestoration> calls this.
window.scrollTo = () => {};
afterAll(() => server.close());

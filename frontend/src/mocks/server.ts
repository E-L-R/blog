/**
 * The mock API for tests, which run in Node: MSW intercepts `fetch` there. Each test gets a
 * fresh database (src/test/setup.ts).
 */
import { setupServer } from 'msw/node';
import { MockDb } from './db';
import { createHandlers } from './handlers';

/** The current test's database. Replaced before every test. */
export let db = new MockDb();

export const server = setupServer();

export function resetMockApi(): MockDb {
  // The fake AI answers at once; tests that need it pending set the delays themselves.
  db = new MockDb({ tldrDelayMs: 0, replyDelayMs: 0 });
  server.resetHandlers(...createHandlers(db));
  return db;
}

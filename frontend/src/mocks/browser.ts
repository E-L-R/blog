/**
 * Starts the mock API in the browser for `npm run dev:mock`. MSW's service worker intercepts
 * the app's requests to /api and answers them with the handlers.
 *
 * The data is kept in `localStorage`, so it survives a reload. To start over, clear the
 * site's data in the browser, or run `localStorage.removeItem('blog.mockDb')` in the console.
 */
import { setupWorker } from 'msw/browser';
import { MockDb, type MockState } from './db';
import { createHandlers } from './handlers';
import { seed } from './seed';

const STORAGE_KEY = 'blog.mockDb';

export async function startMockApi(): Promise<void> {
  const db = new MockDb({}, load());
  if (db.state.posts.length === 0 && db.state.users.length === 1) {
    seed(db);
    save(db.state);
  }
  const worker = setupWorker(...createHandlers(db, () => save(db.state)));
  // Requests the handlers don't cover (the app's own files) go to the dev server as usual.
  await worker.start({ onUnhandledFrame: 'bypass', quiet: true });
  console.info(
    '%c[mock API]%c Answering /api in the browser. Log in as demo@example.com / password123.',
    'color:#5b4cdb;font-weight:bold',
    '',
  );
}

function load(): MockState | undefined {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw === null ? undefined : (JSON.parse(raw) as MockState);
  } catch {
    return undefined;
  }
}

function save(state: MockState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Storage full or blocked: the data lasts until the page is reloaded.
  }
}

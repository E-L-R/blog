/**
 * Keeps the access token from `POST /auth/login`, and tells subscribers when it changes.
 *
 * The token lives in `localStorage`, so a login survives a page reload and is shared by the
 * app's tabs. The trade-off (a script injected into the page could read it) is explained in
 * docs/DECISIONS.md, D19. It sits in the shared `api` layer, not in the `auth` feature,
 * because the API client needs it for every request.
 */

const STORAGE_KEY = 'blog.auth';

interface StoredToken {
  accessToken: string;
  /** Milliseconds since the epoch. */
  expiresAt: number;
}

export interface TokenStore {
  /** The token, or `null` if there is none or it has expired. */
  get(): string | null;
  /** When the current token expires (milliseconds since the epoch), or `null`. */
  expiresAt(): number | null;
  /** Stores a token that expires `expiresIn` seconds from now. */
  set(accessToken: string, expiresIn: number): void;
  clear(): void;
  /** Calls `listener` whenever the token changes, here or in another tab. Returns an unsubscribe function. */
  subscribe(listener: () => void): () => void;
}

/**
 * Creates a token store. `now` gives the current time in milliseconds; tests pass their own,
 * the way backend code takes the time from the injected `Clock`.
 */
export function createTokenStore(storage: Storage, now: () => number = Date.now): TokenStore {
  const listeners = new Set<() => void>();

  function read(): StoredToken | null {
    try {
      const raw = storage.getItem(STORAGE_KEY);
      if (raw === null) return null;
      const parsed = JSON.parse(raw) as Partial<StoredToken>;
      if (typeof parsed.accessToken !== 'string' || typeof parsed.expiresAt !== 'number') {
        return null;
      }
      return { accessToken: parsed.accessToken, expiresAt: parsed.expiresAt };
    } catch {
      // Unreadable storage (private mode, blocked site data) or a corrupt entry: no token.
      return null;
    }
  }

  function notify() {
    listeners.forEach((listener) => listener());
  }

  function write(value: StoredToken | null) {
    try {
      if (value === null) storage.removeItem(STORAGE_KEY);
      else storage.setItem(STORAGE_KEY, JSON.stringify(value));
    } catch {
      // Storage refused the write. The user stays logged in until the page is reloaded.
    }
    notify();
  }

  return {
    get() {
      const stored = read();
      return stored !== null && stored.expiresAt > now() ? stored.accessToken : null;
    },
    expiresAt() {
      return read()?.expiresAt ?? null;
    },
    set(accessToken, expiresIn) {
      write({ accessToken, expiresAt: now() + expiresIn * 1000 });
    },
    clear() {
      if (read() !== null) write(null);
    },
    subscribe(listener) {
      listeners.add(listener);
      // The storage event fires when another tab logs in or out.
      const onStorage = (event: StorageEvent) => {
        if (event.key === STORAGE_KEY || event.key === null) listener();
      };
      window.addEventListener('storage', onStorage);
      return () => {
        listeners.delete(listener);
        window.removeEventListener('storage', onStorage);
      };
    },
  };
}

/** The app's token store. */
export const tokenStore = createTokenStore(window.localStorage);

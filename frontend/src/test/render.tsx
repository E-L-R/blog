/**
 * Helpers for page tests: render the real app at a URL, and log a user in.
 */
import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { tokenStore } from '@/api/token';
import { AppProviders } from '@/app/AppProviders';
import { createQueryClient } from '@/app/queryClient';
import { routes } from '@/app/routes';
import type { MockUser } from '@/mocks/db';
import { db } from '@/mocks/server';

/**
 * Renders the whole app, with its real routes and providers, at `path`. Returns the router
 * too, so a test can check where the app navigated.
 */
export function renderApp(path = '/') {
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({
    queries: { ...queryClient.getDefaultOptions().queries, retry: false },
  });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const result = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...result, router, queryClient };
}

let counter = 0;

/** A new user with values no other test uses. The password is always `password123`. */
export function createUser(name = 'user'): MockUser {
  counter += 1;
  const username = `${name}${counter}`;
  return db.createUser(`${username}@example.com`, username, 'password123');
}

/** Logs `user` in, as a successful `POST /auth/login` would. */
export function logIn(user: MockUser): void {
  tokenStore.set(`mock-token.${user.id}`, 3600);
}

/** A new user, logged in. */
export function createLoggedInUser(name = 'user'): MockUser {
  const user = createUser(name);
  logIn(user);
  return user;
}

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { tokenStore } from '@/api/token';
import { db } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

async function logInWith(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

describe('LoginPage', () => {
  it('logs in and shows the user in the header', async () => {
    const account = createUser('alice');
    const { router } = renderApp('/login');

    await logInWith(account.email!, 'password123');

    const nav = await screen.findByRole('navigation', { name: 'Account' });
    expect(await within(nav).findByText(account.username)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(tokenStore.get()).not.toBeNull();
  });

  it('returns to the page that asked for a login', async () => {
    const account = createUser();
    const { router } = renderApp('/login?redirect=%2Faccount');

    await logInWith(account.email!, 'password123');

    expect(await screen.findByRole('heading', { name: account.username })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/account');
  });

  it('never redirects to another site', async () => {
    const account = createUser();
    const { router } = renderApp('/login?redirect=https%3A%2F%2Fevil.example');

    await logInWith(account.email!, 'password123');

    await screen.findByRole('heading', { name: 'Latest posts' });
    expect(router.state.location.pathname).toBe('/');
  });

  it('gives the same answer for a wrong password and an unknown email', async () => {
    const account = createUser();
    renderApp('/login');

    await logInWith(account.email!, 'wrong password');
    const wrongPassword = (await screen.findByRole('alert')).textContent;
    await userEvent.clear(screen.getByLabelText('Email'));
    await logInWith('nobody@example.com', 'password123');
    const unknownEmail = (await screen.findByRole('alert')).textContent;

    expect(wrongPassword).toBe('Wrong email or password.');
    expect(unknownEmail).toBe(wrongPassword);
    expect(tokenStore.get()).toBeNull();
  });

  it('asks for both fields before sending', async () => {
    renderApp('/login');

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(/enter your email/i);
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/enter your password/i);
  });

  it('cannot log in to a deleted account', async () => {
    const account = createUser();
    const email = account.email!;
    db.deleteUser(account);
    renderApp('/login');

    await logInWith(email, 'password123');

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong email or password.');
  });

  it('sends a logged-in user on', async () => {
    createLoggedInUser();
    const { router } = renderApp('/login');

    await screen.findByRole('heading', { name: 'Latest posts' });
    expect(router.state.location.pathname).toBe('/');
  });
});

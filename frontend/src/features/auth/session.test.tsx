import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { tokenStore } from '@/api/token';
import { db } from '@/mocks/server';
import { createLoggedInUser, createUser, logIn, renderApp } from '@/test/render';

describe('the login session', () => {
  it('sends anonymous users to the login page, and back after logging in', async () => {
    const account = createUser();
    const { router } = renderApp('/posts/new');

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?redirect=%2Fposts%2Fnew');

    await userEvent.type(screen.getByLabelText('Email'), account.email!);
    await userEvent.type(screen.getByLabelText('Password'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('heading', { name: 'New post' })).toBeInTheDocument();
  });

  it('logs out: the token is forgotten and the header changes', async () => {
    createLoggedInUser();
    renderApp('/');

    await userEvent.click(await screen.findByRole('button', { name: 'Log out' }));

    const nav = screen.getByRole('navigation', { name: 'Account' });
    expect(within(nav).getByRole('link', { name: 'Log in' })).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });

  it('drops the token of a deleted account, and the user is logged out', async () => {
    const account = createUser();
    logIn(account);
    db.deleteUser(account);
    renderApp('/');

    const nav = await screen.findByRole('navigation', { name: 'Account' });
    expect(await within(nav).findByRole('link', { name: 'Log in' })).toBeInTheDocument();
    expect(tokenStore.get()).toBeNull();
  });
});

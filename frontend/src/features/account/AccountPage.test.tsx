import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { tokenStore } from '@/api/token';
import { db } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

describe('AccountPage', () => {
  it("shows the account and only the user's own posts", async () => {
    const me = createLoggedInUser('me');
    const other = createUser('other');
    db.createPost(me.id, 'Mine', 'Text.');
    db.createPost(other.id, 'Theirs', 'Text.');
    renderApp('/account');

    expect(await screen.findByRole('heading', { name: me.username })).toBeInTheDocument();
    expect(screen.getByText(me.email!)).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Mine' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Theirs' })).not.toBeInTheDocument();
  });

  it('needs a login', async () => {
    const { router } = renderApp('/account');

    await screen.findByRole('heading', { name: 'Welcome back' });
    expect(router.state.location.pathname).toBe('/login');
  });

  it('refuses to delete the account with a wrong password, and changes nothing', async () => {
    const me = createLoggedInUser();
    renderApp('/account');

    await userEvent.click(await screen.findByRole('button', { name: 'Delete my account…' }));
    await userEvent.type(screen.getByLabelText('Confirm with your password'), 'wrong password');
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }));

    expect(await screen.findByLabelText('Confirm with your password')).toHaveAccessibleDescription(
      'Wrong password.',
    );
    expect(db.findUser(me.id)?.deleted).toBe(false);
    expect(tokenStore.get()).not.toBeNull();
  });

  it('deletes the account: the user is logged out and their posts show "[deleted]"', async () => {
    const me = createLoggedInUser();
    db.createPost(me.id, 'Left behind', 'Text.');
    const { router } = renderApp('/account');

    await userEvent.click(await screen.findByRole('button', { name: 'Delete my account…' }));
    await userEvent.type(screen.getByLabelText('Confirm with your password'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }));

    expect(await screen.findByText('Your account was deleted.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(tokenStore.get()).toBeNull();
    const card = (await screen.findByRole('link', { name: 'Left behind' })).closest('article')!;
    expect(within(card).getByText('[deleted]')).toBeInTheDocument();
    expect(db.findUser(me.id)).toMatchObject({ deleted: true, email: null });
  });
});

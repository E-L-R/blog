import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BOT_USERNAME } from '@/mocks/db';
import { db } from '@/mocks/server';
import { createUser, renderApp } from '@/test/render';

describe('UserPage', () => {
  it("shows a user's profile and their posts", async () => {
    const author = createUser('ada');
    db.createPost(author.id, 'By Ada', 'Text.');
    renderApp(`/users/${author.id}`);

    expect(
      await screen.findByRole('heading', { level: 1, name: author.username }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'By Ada' })).toBeInTheDocument();
  });

  it('marks the AI bot as such', async () => {
    const bot = db.state.users.find((user) => user.username === BOT_USERNAME)!;
    renderApp(`/users/${bot.id}`);

    expect(await screen.findByText('AI assistant')).toBeInTheDocument();
  });

  it('shows a deleted account as not found', async () => {
    const gone = createUser();
    db.deleteUser(gone);
    renderApp(`/users/${gone.id}`);

    expect(await screen.findByRole('heading', { name: 'User not found' })).toBeInTheDocument();
  });
});

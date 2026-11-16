import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { db } from '@/mocks/server';
import { createUser, renderApp } from '@/test/render';
import { PAGE_SIZE } from './api';

function postTitles() {
  return screen
    .getAllByRole('article')
    .map((card) => within(card).getByRole('heading').textContent);
}

describe('PostListPage', () => {
  it('lists posts newest first, with their TL;DR and author', async () => {
    const alice = createUser('alice');
    db.createPost(alice.id, 'First', 'The first post. More text.');
    db.createPost(alice.id, 'Second', 'The second post.');
    renderApp('/');

    expect(await screen.findByRole('link', { name: 'Second' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^\/posts\//),
    );
    expect(postTitles()).toEqual(['Second', 'First']);
    expect(screen.getByText('The first post.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: alice.username })).toHaveLength(2);
  });

  it('shows "[deleted]" for the author of a deleted account', async () => {
    const gone = createUser();
    db.createPost(gone.id, 'Orphan', 'Still here.');
    db.deleteUser(gone);
    renderApp('/');

    const card = (await screen.findByRole('link', { name: 'Orphan' })).closest('article')!;
    expect(within(card).getByText('[deleted]')).toBeInTheDocument();
    expect(within(card).queryByRole('link', { name: gone.username })).not.toBeInTheDocument();
  });

  it('invites the first post when there are none', async () => {
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'No posts yet' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Write the first one' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fposts%2Fnew',
    );
  });

  it('pages through older posts, keeping the page in the URL', async () => {
    const author = createUser();
    for (let i = 1; i <= PAGE_SIZE + 2; i++) db.createPost(author.id, `Post ${i}`, 'Text.');
    const { router } = renderApp('/');

    await screen.findByRole('link', { name: `Post ${PAGE_SIZE + 2}` });
    expect(screen.getAllByRole('article')).toHaveLength(PAGE_SIZE);
    expect(screen.getByText(`Page 1 of 2`)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: /older/i }));

    expect(await screen.findByRole('link', { name: 'Post 1' })).toBeInTheDocument();
    expect(postTitles()).toEqual(['Post 2', 'Post 1']);
    expect(router.state.location.search).toBe('?page=2');
    expect(screen.queryByRole('link', { name: /older/i })).not.toBeInTheDocument();
  });
});

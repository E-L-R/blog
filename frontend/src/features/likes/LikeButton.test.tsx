import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { db, server } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

describe('likes', () => {
  it('likes a post: the heart fills and the count goes up at once', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Likeable', 'Text.');
    const me = createLoggedInUser();
    server.use(
      http.put('*/api/posts/:id/like', async () => {
        await delay(200);
        return undefined; // then the mock API answers as usual
      }),
    );
    renderApp(`/posts/${post.id}`);

    const button = await screen.findByRole('button', { name: /like this post \(0 likes\)/i });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(button);

    // Before the API has answered.
    expect(button).toHaveAttribute('aria-pressed', 'true');
    expect(button).toHaveAccessibleName(/unlike this post \(1 like\)/i);
    await waitFor(() => expect(db.likedIds('post', me.id, [post.id])).toEqual([post.id]));
  });

  it('unlikes a post the user likes', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Liked', 'Text.');
    const me = createLoggedInUser();
    db.like('post', me.id, post.id, true);
    renderApp(`/posts/${post.id}`);

    const button = await screen.findByRole('button', { name: /unlike this post \(1 like\)/i });
    await userEvent.click(button);

    expect(button).toHaveAccessibleName(/^like this post \(0 likes\)/i);
    await waitFor(() => expect(db.likedIds('post', me.id, [post.id])).toEqual([]));
  });

  it('undoes the like when the request fails', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Fragile', 'Text.');
    createLoggedInUser();
    server.use(
      http.put('*/api/posts/:id/like', () =>
        HttpResponse.json({ status: 500, code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    );
    renderApp(`/posts/${post.id}`);

    await userEvent.click(await screen.findByRole('button', { name: /like this post/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i);
    const button = screen.getByRole('button', { name: /like this post \(0 likes\)/i });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('finds out which comments the user likes with one request for the whole thread', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Popular', 'Text.');
    const me = createLoggedInUser();
    const liked = db.createComment(post.id, author.id, 'Liked one', null);
    db.createComment(post.id, author.id, 'Other one', null);
    db.createComment(post.id, author.id, 'A reply', liked.id);
    db.like('comment', me.id, liked.id, true);
    const lookups: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/me/likes/comments')) lookups.push(request.url);
    });
    renderApp(`/posts/${post.id}`);

    const likedArticle = (await screen.findByText('Liked one')).closest('article')!;
    expect(
      await within(likedArticle).findByRole('button', { name: /unlike this comment \(1 like\)/i }),
    ).toHaveAttribute('aria-pressed', 'true');
    const otherArticle = screen.getByText('Other one').closest('article')!;
    expect(
      within(otherArticle).getByRole('button', { name: /like this comment/i }),
    ).toHaveAttribute('aria-pressed', 'false');
    expect(lookups).toHaveLength(1);
    expect(new URL(lookups[0]!).searchParams.get('ids')!.split(',')).toHaveLength(3);
  });

  it('sends visitors to log in, and asks the API nothing about their likes', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Public', 'Text.');
    db.createComment(post.id, author.id, 'A comment', null);
    let lookups = 0;
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/me/likes/')) lookups++;
    });
    const { router } = renderApp(`/posts/${post.id}`);

    await screen.findByText('A comment');
    await userEvent.click(screen.getByRole('button', { name: /like this post/i }));

    expect(router.state.location.pathname).toBe('/login');
    expect(lookups).toBe(0);
  });

  it('shows the like count on the post list', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Counted', 'Text.');
    db.like('post', author.id, post.id, true);
    renderApp('/');

    const card = (await screen.findByRole('link', { name: 'Counted' })).closest('article')!;
    expect(within(card).getByLabelText('1 like')).toBeInTheDocument();
  });
});

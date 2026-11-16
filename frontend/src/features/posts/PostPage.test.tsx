import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { db } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

describe('PostPage', () => {
  it('shows the post, its author and its TL;DR', async () => {
    const author = createUser('writer');
    const post = db.createPost(author.id, 'A title', 'First sentence. Second sentence.');
    renderApp(`/posts/${post.id}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'A title' })).toBeInTheDocument();
    expect(screen.getByText('First sentence. Second sentence.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: author.username })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'AI summary' })).toHaveTextContent(
      'First sentence.',
    );
    expect(document.title).toBe('A title · Blog');
  });

  it('shows content as plain text, never as HTML', async () => {
    const author = createUser();
    const post = db.createPost(
      author.id,
      '<i>Title</i>',
      'Some <b>bold</b> claim.\n<img src=x onerror=alert(1)>',
    );
    const { container } = renderApp(`/posts/${post.id}`);

    expect(
      await screen.findByRole('heading', { level: 1, name: '<i>Title</i>' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/<img src=x onerror=alert\(1\)>/)).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'AI summary' })).toHaveTextContent(
      'Some <b>bold</b> claim.',
    );
    expect(container.querySelector('img, i, b')).toBeNull();
  });

  it('offers Edit and Delete to the author only', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Mine', 'Text.');
    createLoggedInUser('reader');
    renderApp(`/posts/${post.id}`);

    await screen.findByRole('heading', { level: 1, name: 'Mine' });
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('lets the author delete the post after confirming', async () => {
    const author = createLoggedInUser();
    const post = db.createPost(author.id, 'Doomed', 'Text.');
    const { router } = renderApp(`/posts/${post.id}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
    expect(db.state.posts).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Delete post' }));

    expect(await screen.findByText('Post deleted.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(db.state.posts).toHaveLength(0);
    expect(screen.queryByRole('link', { name: 'Doomed' })).not.toBeInTheDocument();
  });

  it('can cancel a deletion', async () => {
    const author = createLoggedInUser();
    const post = db.createPost(author.id, 'Kept', 'Text.');
    renderApp(`/posts/${post.id}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(db.state.posts).toHaveLength(1);
  });

  it('says so when the post does not exist', async () => {
    renderApp('/posts/00000000-0000-7000-8000-000000000000');

    expect(await screen.findByRole('heading', { name: 'Post not found' })).toBeInTheDocument();
  });

  it('shows the new TL;DR once the AI has written it', async () => {
    db.tldrDelayMs = 300;
    const author = createUser();
    const post = db.createPost(author.id, 'Fresh', 'Brand new text. More.');
    renderApp(`/posts/${post.id}`);

    expect(await screen.findByText('Writing a summary…')).toBeInTheDocument();
    expect(await screen.findByText('Brand new text.', {}, { timeout: 5000 })).toBeInTheDocument();
  });
});

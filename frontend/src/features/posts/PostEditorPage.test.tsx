import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { db, server } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

describe('NewPostPage', () => {
  it('publishes a post and opens it', async () => {
    const author = createLoggedInUser();
    const { router } = renderApp('/posts/new');

    await userEvent.type(await screen.findByLabelText('Title'), 'My first post');
    await userEvent.type(screen.getByLabelText('Content'), 'Hello.{enter}{enter}Second paragraph.');
    await userEvent.click(screen.getByRole('button', { name: 'Publish' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'My first post' }),
    ).toBeInTheDocument();
    const [post] = db.state.posts;
    expect(post).toMatchObject({ authorId: author.id, content: 'Hello.\n\nSecond paragraph.' });
    expect(router.state.location.pathname).toBe(`/posts/${post!.id}`);
  });

  it('asks for a title and content before publishing', async () => {
    createLoggedInUser();
    renderApp('/posts/new');

    await userEvent.click(await screen.findByRole('button', { name: 'Publish' }));

    expect(screen.getByLabelText('Title')).toHaveAccessibleDescription(/title/i);
    expect(screen.getByLabelText('Content')).toHaveAccessibleDescription(/write something/i);
    expect(db.state.posts).toHaveLength(0);
  });

  it('counts characters near the title limit', async () => {
    createLoggedInUser();
    renderApp('/posts/new');

    const title = await screen.findByLabelText('Title');
    await userEvent.click(title);
    await userEvent.paste('a'.repeat(201));

    expect(screen.getByText('201 / 200')).toBeInTheDocument();
  });
});

describe('EditPostPage', () => {
  it('saves changes and shows the updated post', async () => {
    const author = createLoggedInUser();
    const post = db.createPost(author.id, 'Old title', 'Old text.');
    renderApp(`/posts/${post.id}/edit`);

    const title = await screen.findByLabelText('Title');
    expect(title).toHaveValue('Old title');
    await userEvent.clear(title);
    await userEvent.type(title, 'New title');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'New title' })).toBeInTheDocument();
    expect(db.findPost(post.id)?.title).toBe('New title');
  });

  it('explains an edit conflict', async () => {
    const author = createLoggedInUser();
    const post = db.createPost(author.id, 'Contested', 'Text.');
    server.use(
      http.put('*/api/posts/:id', () =>
        HttpResponse.json({ status: 409, code: 'EDIT_CONFLICT' }, { status: 409 }),
      ),
    );
    renderApp(`/posts/${post.id}/edit`);

    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/at the same moment/);
  });

  it("doesn't offer the form to someone who isn't the author", async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Not yours', 'Text.');
    createLoggedInUser('other');
    renderApp(`/posts/${post.id}/edit`);

    expect(
      await screen.findByRole('heading', { name: "You can't edit this post" }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Title')).not.toBeInTheDocument();
  });

  it('shows the error if the API refuses the edit anyway', async () => {
    const author = createLoggedInUser();
    const post = db.createPost(author.id, 'Mine', 'Text.');
    server.use(
      http.put('*/api/posts/:id', () =>
        HttpResponse.json({ status: 403, code: 'NOT_AUTHOR' }, { status: 403 }),
      ),
    );
    renderApp(`/posts/${post.id}/edit`);

    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Only the author can do that.');
  });
});

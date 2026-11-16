import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { db } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

function commentsSection() {
  return screen.getByRole('region', { name: /comments/i });
}

/** The replies shown under a comment: the comment's article is followed by its replies. */
function repliesOf(article: HTMLElement) {
  const wrapper = article.parentElement!;
  return within(wrapper)
    .getAllByRole('article')
    .filter((element) => element !== article);
}

describe('CommentSection', () => {
  it('shows the thread as a tree, oldest first, with replies under their parent', async () => {
    const author = createUser('ann');
    const other = createUser('bob');
    const post = db.createPost(author.id, 'Threaded', 'Text.');
    const first = db.createComment(post.id, other.id, 'First comment', null);
    db.createComment(post.id, author.id, 'Second comment', null);
    db.createComment(post.id, author.id, 'A reply to the first', first.id);
    renderApp(`/posts/${post.id}`);

    const firstArticle = await screen.findByRole('article', {
      name: `Comment by ${other.username}`,
    });
    const all = within(commentsSection()).getAllByRole('article');
    expect(
      all.map(
        (article) => within(article).getByText(/comment|reply/i, { selector: 'p' }).textContent,
      ),
    ).toEqual(['First comment', 'A reply to the first', 'Second comment']);
    expect(repliesOf(firstArticle).map((article) => article.textContent)).toEqual([
      expect.stringContaining('A reply to the first'),
    ]);
    expect(
      within(commentsSection()).getByRole('heading', { name: /comments\s*3/i }),
    ).toBeInTheDocument();
  });

  it('asks visitors to log in, instead of offering a comment box', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Public', 'Text.');
    db.createComment(post.id, author.id, 'Hello', null);
    const { router } = renderApp(`/posts/${post.id}`);

    await screen.findByText('Hello');
    expect(screen.queryByLabelText('Write a comment')).not.toBeInTheDocument();
    expect(within(commentsSection()).getByRole('link', { name: 'Log in' })).toHaveAttribute(
      'href',
      `/login?redirect=${encodeURIComponent(`/posts/${post.id}`)}`,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Reply' }));
    expect(router.state.location.pathname).toBe('/login');
  });

  it('adds a comment', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Open', 'Text.');
    const me = createLoggedInUser('me');
    renderApp(`/posts/${post.id}`);

    await userEvent.type(await screen.findByLabelText('Write a comment'), 'Nice post!');
    await userEvent.click(screen.getByRole('button', { name: 'Comment' }));

    const article = await screen.findByRole('article', { name: `Comment by ${me.username}` });
    expect(article).toHaveTextContent('Nice post!');
    expect(screen.getByLabelText('Write a comment')).toHaveValue('');
    expect(db.state.comments).toMatchObject([{ postId: post.id, authorId: me.id, parentId: null }]);
  });

  it('asks for some text before sending a comment', async () => {
    const author = createLoggedInUser();
    const post = db.createPost(author.id, 'Open', 'Text.');
    renderApp(`/posts/${post.id}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Comment' }));

    expect(screen.getByLabelText('Write a comment')).toHaveAccessibleDescription(
      /write something/i,
    );
    expect(db.state.comments).toHaveLength(0);
  });

  it('replies to a comment, and shows the reply under it', async () => {
    const author = createUser('ann');
    const post = db.createPost(author.id, 'Talk', 'Text.');
    const parent = db.createComment(post.id, author.id, 'What do you think?', null);
    createLoggedInUser('me');
    renderApp(`/posts/${post.id}`);

    const parentArticle = await screen.findByRole('article', {
      name: `Comment by ${author.username}`,
    });
    await userEvent.click(within(parentArticle).getByRole('button', { name: 'Reply' }));
    await userEvent.type(screen.getByLabelText(`Reply to ${author.username}`), 'I agree.');
    await userEvent.click(within(parentArticle).getByRole('button', { name: 'Post reply' }));

    expect(await screen.findByText('I agree.')).toBeInTheDocument();
    expect(repliesOf(parentArticle)[0]).toHaveTextContent('I agree.');
    expect(db.state.comments.find((c) => c.content === 'I agree.')?.parentId).toBe(parent.id);
  });

  it('lets the author edit their comment, and nobody else', async () => {
    const other = createUser('other');
    const post = db.createPost(other.id, 'Talk', 'Text.');
    db.createComment(post.id, other.id, 'Not mine', null);
    const me = createLoggedInUser('me');
    db.createComment(post.id, me.id, 'Typo hree', null);
    renderApp(`/posts/${post.id}`);

    const theirs = await screen.findByRole('article', { name: `Comment by ${other.username}` });
    expect(within(theirs).queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(within(theirs).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();

    const mine = screen.getByRole('article', { name: `Comment by ${me.username}` });
    await userEvent.click(within(mine).getByRole('button', { name: 'Edit' }));
    const box = within(mine).getByLabelText('Edit your comment');
    await userEvent.clear(box);
    await userEvent.type(box, 'Typo here');
    await userEvent.click(within(mine).getByRole('button', { name: 'Save' }));

    expect(await within(mine).findByText('Typo here')).toBeInTheDocument();
    expect(within(mine).getByText(/edited/)).toBeInTheDocument();
  });

  it('deletes a comment but keeps its replies, shown under "[deleted]"', async () => {
    const me = createLoggedInUser('me');
    const other = createUser('other');
    const post = db.createPost(me.id, 'Talk', 'Text.');
    const mine = db.createComment(post.id, me.id, 'Regrettable', null);
    db.createComment(post.id, other.id, 'A reply that stays', mine.id);
    renderApp(`/posts/${post.id}`);

    const article = await screen.findByRole('article', { name: `Comment by ${me.username}` });
    await userEvent.click(within(article).getByRole('button', { name: 'Delete' }));
    await userEvent.click(within(article).getByRole('button', { name: 'Delete comment' }));

    const deleted = await screen.findByRole('article', { name: 'Deleted comment' });
    expect(deleted).toHaveTextContent('[deleted]');
    expect(screen.queryByText('Regrettable')).not.toBeInTheDocument();
    expect(within(deleted).queryByRole('button')).not.toBeInTheDocument();
    expect(repliesOf(deleted)[0]).toHaveTextContent('A reply that stays');
  });

  it('explains when the comment being answered was deleted in the meantime', async () => {
    const other = createUser('other');
    const post = db.createPost(other.id, 'Talk', 'Text.');
    const parent = db.createComment(post.id, other.id, 'Soon gone', null);
    createLoggedInUser('me');
    renderApp(`/posts/${post.id}`);

    const article = await screen.findByRole('article', { name: `Comment by ${other.username}` });
    await userEvent.click(within(article).getByRole('button', { name: 'Reply' }));
    await userEvent.type(screen.getByLabelText(`Reply to ${other.username}`), 'Too late');
    db.deleteComment(parent);
    await userEvent.click(within(article).getByRole('button', { name: 'Post reply' }));

    expect(await within(article).findByRole('alert')).toHaveTextContent('deleted in the meantime');
  });

  it('keeps the text of a deleted account\'s comment, by "[deleted]"', async () => {
    const gone = createUser('gone');
    const post = db.createPost(gone.id, 'Old', 'Text.');
    db.createComment(post.id, gone.id, 'Still readable', null);
    db.deleteUser(gone);
    renderApp(`/posts/${post.id}`);

    const article = await screen.findByRole('article', { name: 'Comment by [deleted]' });
    expect(article).toHaveTextContent('Still readable');
    expect(within(article).getByText('[deleted]')).toBeInTheDocument();
  });

  it('shows comment content as text, never as HTML', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Safe', 'Text.');
    db.createComment(post.id, author.id, '<img src=x onerror=alert(1)>', null);
    const { container } = renderApp(`/posts/${post.id}`);

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });
});

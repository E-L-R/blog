import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { BOT_USERNAME } from '@/mocks/db';
import { db, server } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

describe('AskAiButton', () => {
  it("asks the AI to reply to a post, and shows the bot's comment in the thread", async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Question', 'Is a monolith fine? I think so.');
    const me = createLoggedInUser();
    renderApp(`/posts/${post.id}`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Ask AI to reply to this post' }),
    );

    const reply = await screen.findByRole('article', { name: `Comment by ${BOT_USERNAME}` });
    expect(within(reply).getByText('AI')).toBeInTheDocument();
    expect(reply).toHaveTextContent('Is a monolith fine?');
    expect(db.state.aiRequests).toMatchObject([{ requestedById: me.id, status: 'COMPLETED' }]);
  });

  it('asks the AI to reply to a comment, and shows the reply under it', async () => {
    const author = createUser('ann');
    const post = db.createPost(author.id, 'Talk', 'Text.');
    const comment = db.createComment(post.id, author.id, 'Why is the sky blue?', null);
    createLoggedInUser();
    renderApp(`/posts/${post.id}`);

    const article = await screen.findByRole('article', { name: `Comment by ${author.username}` });
    await userEvent.click(
      within(article).getByRole('button', { name: 'Ask AI to reply to this comment' }),
    );

    const reply = await screen.findByRole('article', { name: `Comment by ${BOT_USERNAME}` });
    expect(article.parentElement).toContainElement(reply);
    expect(db.state.comments.find((c) => c.authorId === db.bot.id)?.parentId).toBe(comment.id);
  });

  it('shows that the AI is writing until the reply is there', async () => {
    db.replyDelayMs = 400;
    const author = createUser();
    const post = db.createPost(author.id, 'Slow', 'Text.');
    createLoggedInUser();
    renderApp(`/posts/${post.id}`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Ask AI to reply to this post' }),
    );

    expect(screen.getByText('The AI is writing a reply…')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ask AI to reply to this post' })).toBeDisabled();
    await screen.findByRole('article', { name: `Comment by ${BOT_USERNAME}` }, { timeout: 5000 });
    expect(screen.queryByText('The AI is writing a reply…')).not.toBeInTheDocument();
  });

  it("says so when the AI couldn't reply", async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Unlucky', 'Text.');
    createLoggedInUser();
    server.use(
      http.get('*/api/ai-requests/:id', ({ params }) =>
        HttpResponse.json({
          id: params.id,
          status: 'FAILED',
          postId: post.id,
          commentId: null,
          resultCommentId: null,
          createdAt: '2026-11-16T12:00:00Z',
          completedAt: '2026-11-16T12:00:05Z',
        }),
      ),
    );
    renderApp(`/posts/${post.id}`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Ask AI to reply to this post' }),
    );

    expect(
      await screen.findByText("The AI couldn't reply this time. Try again."),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ask AI to reply to this post' })).toBeEnabled();
  });

  it('cannot ask about a deleted comment, which offers no actions', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Talk', 'Text.');
    const comment = db.createComment(post.id, author.id, 'Gone soon', null);
    db.deleteComment(comment);
    createLoggedInUser();
    renderApp(`/posts/${post.id}`);

    const article = await screen.findByRole('article', { name: 'Deleted comment' });
    expect(within(article).queryByRole('button', { name: /ask ai/i })).not.toBeInTheDocument();
  });

  it('sends visitors to log in', async () => {
    const author = createUser();
    const post = db.createPost(author.id, 'Public', 'Text.');
    const { router } = renderApp(`/posts/${post.id}`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Ask AI to reply to this post' }),
    );

    expect(router.state.location.pathname).toBe('/login');
    expect(db.state.aiRequests).toHaveLength(0);
  });
});

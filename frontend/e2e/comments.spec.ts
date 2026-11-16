import { expect, test } from '@playwright/test';
import { signUpAndLogIn, writePost } from './helpers';

test('readers comment, reply, like, ask the AI, edit and delete', async ({ page }) => {
  await signUpAndLogIn(page, 'talker');
  await writePost(page, `Let's talk ${Date.now()}`, 'Comments, likes and AI replies, end to end.');
  const comments = page.getByRole('region', { name: /comments/i });

  // A comment, and a reply to it.
  await comments.getByLabel('Write a comment').fill('First thought.');
  await comments.getByRole('button', { name: 'Comment', exact: true }).click();
  // Found by its ID from here on: while it is being edited, its text is inside the text box.
  const firstId = await comments
    .getByRole('article')
    .filter({ hasText: 'First thought.' })
    .getAttribute('id');
  const first = page.locator(`[id="${firstId}"]`);
  await expect(first).toBeVisible();
  await first.getByRole('button', { name: 'Reply', exact: true }).click();
  await first.getByRole('textbox').fill('A second thought.');
  await first.getByRole('button', { name: 'Post reply' }).click();
  await expect(
    comments.getByRole('article').filter({ hasText: 'A second thought.' }),
  ).toBeVisible();

  // Likes on the post and on the comment.
  const likePost = page.getByRole('button', { name: /like this post/i });
  await likePost.click();
  await expect(likePost).toHaveAttribute('aria-pressed', 'true');
  await expect(likePost).toHaveAccessibleName(/\(1 like\)/);
  const likeComment = first.getByRole('button', { name: /like this comment/i });
  await likeComment.click();
  await expect(likeComment).toHaveAttribute('aria-pressed', 'true');

  // The likes are saved: they are still there after a reload.
  await page.reload();
  await expect(page.getByRole('button', { name: /unlike this post \(1 like\)/i })).toBeVisible();

  // The AI replies to the post. Its text depends on the model, so only its arrival is checked.
  await comments.getByRole('button', { name: 'Ask AI to reply to this post' }).click();
  const botReply = comments.getByRole('article', { name: 'Comment by blog-ai' });
  await expect(botReply).toBeVisible({ timeout: 20_000 });
  await expect(botReply.getByText('AI', { exact: true })).toBeVisible();

  // Editing, then deleting: the reply stays, under "[deleted]".
  await first.getByRole('button', { name: 'Edit' }).click();
  await first.getByRole('textbox').fill('First thought, edited.');
  await first.getByRole('button', { name: 'Save' }).click();
  await expect(first.getByText('First thought, edited.')).toBeVisible();
  await expect(first.getByText('· edited')).toBeVisible();
  await first.getByRole('button', { name: 'Delete' }).click();
  await first.getByRole('button', { name: 'Delete comment' }).click();
  await expect(comments.getByRole('article', { name: 'Deleted comment' })).toBeVisible();
  await expect(
    comments.getByRole('article').filter({ hasText: 'A second thought.' }),
  ).toBeVisible();
});

test('visitors read the comments but are asked to log in to take part', async ({ page }) => {
  await signUpAndLogIn(page, 'host');
  const title = `Open thread ${Date.now()}`;
  await writePost(page, title, 'Anyone may read the comments.');
  const comments = page.getByRole('region', { name: /comments/i });
  await comments.getByLabel('Write a comment').fill('Visible to all.');
  await comments.getByRole('button', { name: 'Comment', exact: true }).click();
  await expect(comments.getByText('Visible to all.')).toBeVisible();
  await page
    .getByRole('navigation', { name: 'Account' })
    .getByRole('button', { name: 'Log out' })
    .click();

  await page.reload();
  await expect(comments.getByText('Visible to all.')).toBeVisible();
  await expect(comments.getByLabel('Write a comment')).toHaveCount(0);
  await comments.getByRole('button', { name: 'Ask AI to reply to this post' }).click();
  await expect(page).toHaveURL(/\/login\?redirect=/);
});

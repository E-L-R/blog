import { expect, test } from '@playwright/test';
import { signUpAndLogIn, writePost } from './helpers';

test('an author writes, edits and deletes a post, and the AI summarises it', async ({ page }) => {
  await signUpAndLogIn(page, 'author');
  const title = `Testing in a real browser ${Date.now()}`;

  await writePost(
    page,
    title,
    'End-to-end tests catch what unit tests miss. They are slower, so keep them few.',
  );

  // The TL;DR is written in the background; the page asks again until it is there. Its text
  // depends on the model (the mock API's or the backend's fake one), so only its arrival is
  // checked.
  const summary = page.getByRole('complementary', { name: 'AI summary' });
  await expect(summary).toBeVisible();
  await expect(summary).not.toContainText('Writing a summary', { timeout: 15_000 });
  await expect(summary.getByRole('paragraph')).toHaveText(/\w/);

  await page.getByRole('link', { name: 'Edit' }).click();
  await page.getByLabel('Title').fill(`${title}, revised`);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(`${title}, revised`);
  await expect(page.getByText(/edited/)).toBeVisible();

  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await page.getByRole('button', { name: 'Delete post' }).click();

  await expect(page.getByText('Post deleted.')).toBeVisible();
  await expect(page.getByRole('link', { name: `${title}, revised` })).toHaveCount(0);
});

test('visitors read posts but get no edit controls', async ({ page }) => {
  await signUpAndLogIn(page, 'poster');
  const title = `Open to everyone ${Date.now()}`;
  await writePost(page, title, 'Anyone may read this.');
  await page.getByRole('button', { name: 'Log out' }).click();

  await page.goto('/');
  await page.getByRole('link', { name: title }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
  await expect(page.getByText('Anyone may read this.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0);
});

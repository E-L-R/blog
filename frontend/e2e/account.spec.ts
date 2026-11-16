import { expect, test } from '@playwright/test';
import { signUpAndLogIn, writePost } from './helpers';

test('deleting an account keeps its posts, by "[deleted]"', async ({ page }) => {
  const account = await signUpAndLogIn(page, 'leaver');
  const title = `A parting post ${Date.now()}`;
  await writePost(page, title, 'I was here.');

  await page.getByRole('link', { name: /your account/i }).click();
  await page.getByRole('button', { name: 'Delete my account…' }).click();
  await page.getByLabel('Confirm with your password').fill(account.password);
  await page.getByRole('button', { name: 'Delete my account' }).click();

  await expect(page.getByText('Your account was deleted.')).toBeVisible();
  const card = page.getByRole('article').filter({ hasText: title });
  await expect(card.getByText('[deleted]')).toBeVisible();
  // In the header: the posts listed on this page may have the same words in their titles.
  const nav = page.getByRole('navigation', { name: 'Account' });
  await expect(nav.getByRole('link', { name: 'Log in' })).toBeVisible();

  // The username is retired for good.
  await nav.getByRole('link', { name: 'Sign up' }).click();
  await page.getByLabel('Email').fill(`again-${account.email}`);
  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByLabel('Username')).toHaveAccessibleDescription(/taken/);
});

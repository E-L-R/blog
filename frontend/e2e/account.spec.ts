import { expect, test } from '@playwright/test';
import { logIn, uniqueName } from './helpers';

test('deleting an account keeps its posts, by "[deleted]"', async ({ page }) => {
  const username = uniqueName('leaver');
  const email = `${username}@example.com`;
  await page.goto('/register');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('correct horse');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Your account is ready')).toBeVisible();
  await logIn(page, email, 'correct horse');

  await page.getByRole('link', { name: 'Write' }).click();
  await page.getByLabel('Title').fill('A parting post');
  await page.getByLabel('Content').fill('I was here.');
  await page.getByRole('button', { name: 'Publish' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('A parting post');

  await page.getByRole('link', { name: /your account/i }).click();
  await page.getByRole('button', { name: 'Delete my account…' }).click();
  await page.getByLabel('Confirm with your password').fill('correct horse');
  await page.getByRole('button', { name: 'Delete my account' }).click();

  await expect(page.getByText('Your account was deleted.')).toBeVisible();
  const card = page.getByRole('article').filter({ hasText: 'A parting post' });
  await expect(card.getByText('[deleted]')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible();

  // The username is retired for good.
  await page.getByRole('link', { name: 'Sign up' }).click();
  await page.getByLabel('Email').fill(`again-${email}`);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('correct horse');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByLabel('Username')).toHaveAccessibleDescription(/taken/);
});

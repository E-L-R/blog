import { expect, test } from '@playwright/test';
import { uniqueName } from './helpers';

test('a visitor signs up, logs in and logs out', async ({ page }) => {
  const username = uniqueName('reader');
  const email = `${username}@example.com`;

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign up' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('correct horse');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('Your account is ready')).toBeVisible();
  await expect(page.getByLabel('Email')).toHaveValue(email);
  await page.getByLabel('Password').fill('correct horse');
  await page.getByRole('button', { name: 'Log in' }).click();

  const nav = page.getByRole('navigation', { name: 'Account' });
  await expect(nav.getByText(username)).toBeVisible();

  // The login survives a reload: the token is kept in localStorage.
  await page.reload();
  await expect(nav.getByText(username)).toBeVisible();

  await nav.getByRole('button', { name: 'Log out' }).click();
  await expect(nav.getByRole('link', { name: 'Log in' })).toBeVisible();
});

test('a protected page sends the visitor to log in, and back afterwards', async ({ page }) => {
  await page.goto('/posts/new');

  await expect(page).toHaveURL(/\/login\?redirect=%2Fposts%2Fnew$/);
  await page.getByLabel('Email').fill('demo@example.com');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL(/\/posts\/new$/);
  await expect(page.getByRole('heading', { name: 'New post' })).toBeVisible();
});

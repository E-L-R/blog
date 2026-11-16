import { expect, test } from '@playwright/test';
import { newAccount, signUp } from './helpers';

test('a visitor signs up, logs in and logs out', async ({ page }) => {
  const account = newAccount('reader');

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign up' }).click();
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('Your account is ready')).toBeVisible();
  await expect(page.getByLabel('Email')).toHaveValue(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();

  const nav = page.getByRole('navigation', { name: 'Account' });
  await expect(nav.getByText(account.username)).toBeVisible();

  // The login survives a reload: the token is kept in localStorage.
  await page.reload();
  await expect(nav.getByText(account.username)).toBeVisible();

  await nav.getByRole('button', { name: 'Log out' }).click();
  await expect(nav.getByRole('link', { name: 'Log in' })).toBeVisible();
});

test('a wrong password is refused', async ({ page }) => {
  const account = newAccount('forgetful');
  await signUp(page, account);

  await page.getByLabel('Password').fill('not my password');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page.getByRole('alert')).toHaveText('Wrong email or password.');
});

test('a protected page sends the visitor to log in, and back afterwards', async ({ page }) => {
  const account = newAccount('writer');
  await signUp(page, account);

  await page.goto('/posts/new');

  await expect(page).toHaveURL(/\/login\?redirect=%2Fposts%2Fnew$/);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL(/\/posts\/new$/);
  await expect(page.getByRole('heading', { name: 'New post' })).toBeVisible();
});

/**
 * Steps the end-to-end tests share. The tests create their own users and posts through the
 * app, so the same tests run against the mock API and against the real backend
 * (docs/TESTING.md#running-the-frontend-tests).
 */
import { expect, type Page } from '@playwright/test';

export interface Account {
  email: string;
  username: string;
  password: string;
}

/** A new account with a username no other test run uses. Not registered yet. */
export function newAccount(prefix: string): Account {
  const username = `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  return { email: `${username}@example.com`, username, password: 'correct horse' };
}

/** Registers the account through the registration page, which leads to the login page. */
export async function signUp(page: Page, account: Account) {
  await page.goto('/register');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Username').fill(account.username);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Your account is ready')).toBeVisible();
}

export async function logIn(page: Page, account: Account) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
}

/** A new account, registered and logged in. */
export async function signUpAndLogIn(page: Page, prefix: string): Promise<Account> {
  const account = newAccount(prefix);
  await signUp(page, account);
  await logIn(page, account);
  return account;
}

/** Writes a post through the editor and waits until its page shows it. */
export async function writePost(page: Page, title: string, content: string) {
  await page.getByRole('link', { name: 'Write' }).click();
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Content').fill(content);
  await page.getByRole('button', { name: 'Publish' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
}

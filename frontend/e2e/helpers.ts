/**
 * Steps the end-to-end tests share. Every test starts in a new browser context, so it gets
 * empty storage and the mock API's demo data (src/mocks/seed.ts).
 */
import { expect, type Page } from '@playwright/test';

export const DEMO = { email: 'demo@example.com', password: 'password123', username: 'demo' };

export async function logIn(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
}

/** A username no other test run uses. */
export function uniqueName(prefix: string) {
  return `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}

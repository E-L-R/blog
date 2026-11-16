import { expect, test } from '@playwright/test';
import { DEMO, logIn } from './helpers';

test('an author writes, edits and deletes a post, and the AI summarises it', async ({ page }) => {
  await logIn(page, DEMO.email, DEMO.password);

  await page.getByRole('link', { name: 'Write' }).click();
  await page.getByLabel('Title').fill('Testing in a real browser');
  await page
    .getByLabel('Content')
    .fill('End-to-end tests catch what unit tests miss. They are slower, so keep them few.');
  await page.getByRole('button', { name: 'Publish' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Testing in a real browser');
  // The mock AI takes a moment; the page asks again until the TL;DR is there.
  await expect(page.getByText('Writing a summary…')).toBeVisible();
  await expect(page.getByRole('complementary', { name: 'AI summary' })).toContainText(
    'End-to-end tests catch what unit tests miss.',
    { timeout: 10_000 },
  );

  await page.getByRole('link', { name: 'Edit' }).click();
  await page.getByLabel('Title').fill('Testing in a real browser, revised');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    'Testing in a real browser, revised',
  );
  await expect(page.getByText(/edited/)).toBeVisible();

  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await page.getByRole('button', { name: 'Delete post' }).click();

  await expect(page.getByText('Post deleted.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Testing in a real browser, revised' })).toHaveCount(
    0,
  );
});

test('visitors read posts but get no edit controls', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Why I chose a modular monolith' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    'Why I chose a modular monolith',
  );
  await expect(page.getByRole('complementary', { name: 'AI summary' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0);
});

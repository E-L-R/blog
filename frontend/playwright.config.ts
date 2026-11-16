import { defineConfig, devices } from '@playwright/test';

// The end-to-end tests drive a real browser. With this configuration (`npm run e2e`) the app
// runs in mock mode, so they need neither the backend nor Docker. playwright.backend.config.ts
// runs the same tests against the real backend (docs/TESTING.md#frontend-tests).
const port = 4173;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev:mock -- --port ${port}`,
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
  },
});

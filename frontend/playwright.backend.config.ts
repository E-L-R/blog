import { defineConfig } from '@playwright/test';
import base from './playwright.config';

// `npm run e2e:backend`: the end-to-end tests against the real backend, which must already be
// running on localhost:8080 with the dev profile (docs/DEVELOPMENT.md#frontend). The dev server
// forwards /api to it, as in `npm run dev`. Another port than the mock mode's, so a running
// mock server is never mistaken for this one.
const port = 4174;

export default defineConfig({
  ...base,
  use: { ...base.use, baseURL: `http://localhost:${port}` },
  webServer: {
    command: `npm run dev -- --port ${port}`,
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
  },
});

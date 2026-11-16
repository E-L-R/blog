import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { msw } from 'msw/vite';
import { defineConfig } from 'vitest/config';

// One file configures the dev server, the production build and the unit tests (Vitest reads
// the `test` section).
export default defineConfig({
  plugins: [
    react(),
    // Serves MSW's service worker script for `npm run dev:mock`. `apply: 'serve'` keeps the
    // plugin out of production builds, which contain neither the worker nor the mock handlers.
    { ...msw({ mode: 'worker-only' }), apply: 'serve' },
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    strictPort: true,
    // The app calls the API under /api. The dev server forwards those requests to the
    // backend without the prefix, so the browser only ever talks to one origin and the
    // backend needs no CORS settings (docs/DECISIONS.md, D19).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    // CSS modules return their real class names, so tests can't depend on styling.
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
});

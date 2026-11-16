/**
 * The entry point. In mock mode (`npm run dev:mock`) it first starts the mock API in the
 * browser, so the app's first request is already answered by it.
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './styles/tokens.css';
import './styles/global.css';

async function enableMocking() {
  // `import.meta.env.DEV` is false in production builds, so the bundler drops this branch,
  // and with it every mock file.
  if (!import.meta.env.DEV || import.meta.env.VITE_API_MOCK !== 'true') return;
  const { startMockApi } = await import('./mocks/browser');
  await startMockApi();
}

void enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});

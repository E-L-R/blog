/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** `"true"` in mock mode (`npm run dev:mock`): the browser answers API calls itself. */
  readonly VITE_API_MOCK?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

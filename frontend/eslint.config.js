import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * The features and the other features each one may use: the frontend's version of
 * `allowedDependencies` in the backend's package-info.java files (docs/FRONTEND.md#features).
 * A feature is used only through its index.ts, never through its other files.
 */
const allowedFeatureDependencies = {
  auth: [],
  posts: ['auth'],
  account: ['auth', 'posts'],
};

const features = Object.keys(allowedFeatureDependencies);

// Imports that leave the current folder go through the `@/` alias, so every rule below only
// has to look at `@/...` paths. A deep import such as `@/features/posts/api` is refused: the
// index is the feature's public API, like a Spring Modulith module's base package.
const sharedRestrictions = [
  {
    group: ['../*'],
    message: 'Import files outside this folder with the @/ alias, for example @/components/Button.',
  },
  {
    group: ['@/features/*/*'],
    message: "Use the feature's public API, its index.ts: import from '@/features/<name>'.",
  },
  {
    group: ['@/mocks', '@/mocks/*', '@/test', '@/test/*'],
    message: 'Production code must not use the mock API or the test helpers.',
  },
];

const featureRules = features.map((feature) => {
  const forbidden = features.filter(
    (other) => other !== feature && !allowedFeatureDependencies[feature].includes(other),
  );
  return {
    files: [`src/features/${feature}/**/*.{ts,tsx}`],
    ignores: ['**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            ...sharedRestrictions,
            {
              group: [`@/features/${feature}`],
              message: 'Inside a feature, import its own files with ./ instead of its index.',
            },
            ...(forbidden.length > 0
              ? [
                  {
                    group: forbidden.map((name) => `@/features/${name}`),
                    message: `The ${feature} feature may only use: ${allowedFeatureDependencies[feature].join(', ') || 'no other feature'}.`,
                  },
                ]
              : []),
          ],
        },
      ],
    },
  };
});

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    rules: {
      // Content is plain text (docs/DECISIONS.md, D12) and React escapes it. Inserting raw
      // HTML would let a post run scripts in the reader's browser, and read their token.
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'Never insert HTML: show content as text (SECURITY.md#frontend).',
        },
      ],
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['**/*.test.{ts,tsx}', 'src/mocks/**', 'src/test/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: sharedRestrictions }],
    },
  },
  {
    // Shared code sits below the features, so it never depends on one.
    files: ['src/api/**/*.{ts,tsx}', 'src/components/**/*.{ts,tsx}', 'src/lib/**/*.{ts,tsx}'],
    ignores: ['**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            ...sharedRestrictions,
            {
              group: ['@/features', '@/features/*', '@/app', '@/app/*'],
              message: 'Shared code (api, components, lib) must not depend on features or the app.',
            },
          ],
        },
      ],
    },
  },
  ...featureRules,
  {
    // Tests and the mock API may reach into any file, like the backend's module tests.
    files: ['**/*.test.{ts,tsx}', 'src/test/**', 'src/mocks/**', 'e2e/**'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
  {
    files: ['vite.config.ts', 'playwright.config.ts', 'e2e/**', 'eslint.config.js'],
    languageOptions: { globals: globals.node },
  },
]);

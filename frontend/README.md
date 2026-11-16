# Blog frontend

The web app for the [Blog API](../README.md): a React single-page app, written in TypeScript and built with Vite.

```bash
npm install
npm run dev:mock   # the app with a mock API in the browser: no backend or Docker needed
npm run dev        # the app against the backend on localhost:8080
npm run check      # lint, formatting, types, unit tests and the build, as CI runs them
npm run e2e        # end-to-end tests in a real browser, against the mock API
npm run e2e:backend  # the same tests, against the backend running on localhost:8080
```

Everything else is in the project's documentation:

- [docs/FRONTEND.md](../docs/FRONTEND.md): how the app is built: features and their boundaries, data fetching, the mock API, styling, conventions
- [docs/DEVELOPMENT.md](../docs/DEVELOPMENT.md#frontend): installing, running and testing it
- [docs/TESTING.md](../docs/TESTING.md#frontend-tests): the test strategy and the tests each feature needs

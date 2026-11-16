# CLAUDE.md

Guidance for Claude when working on this repository.

## The project

A REST backend for a blogging platform with AI features (an AI TL;DR per post, AI replies posted as comments by a bot user). Spring Boot 4.1 on Java 25, built as a modular monolith with Spring Modulith, PostgreSQL 18, Flyway and Testcontainers.

A React web app lives in `frontend/`: React 19, TypeScript, Vite, React Router, TanStack Query, tested with Vitest, Testing Library, Mock Service Worker and Playwright. It is built against the API design, with a mock API that implements it.

The documentation is the source of truth. Read the relevant file before answering instead of guessing:

- [README.md](README.md): status, features and the roadmap (which steps are done)
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md): building, running, testing, and the checklist for adding a module
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): modules, allowed dependencies, coding conventions
- [docs/FRONTEND.md](docs/FRONTEND.md): the web app: features and their boundaries, pages, the mock API, conventions
- [docs/DOMAIN.md](docs/DOMAIN.md), [docs/DATABASE.md](docs/DATABASE.md), [docs/API.md](docs/API.md): the design of each feature
- [docs/TESTING.md](docs/TESTING.md): test strategy, conventions and the tests each feature needs
- [docs/DECISIONS.md](docs/DECISIONS.md): why things are the way they are
- [CONTRIBUTING.md](CONTRIBUTING.md): branches, commit messages, pull requests, code style and comments, definition of done

Design docs mark anything not built yet as *planned*. Check the code before assuming a planned part exists.

## How we work

- **The user writes and commits all the code.** They are learning: they know basic Spring and layered architecture. Never commit, push or merge, and don't change code files unless asked.
- **Reviews:** when asked to check their work, say what is right, what is wrong and what is missing, and explain why.
- **Instructions:** when asked how to do something, give exact file-by-file instructions. Test them first in a scratch copy of the repo, so they are known to work. Explain the ideas step by step, from the basics.
- **Docs:** edit documentation when asked. When a roadmap step is finished, its *planned* notes, the README roadmap and the CHANGELOG need updating.
- Suggest commit messages and branch names in the project's format (see CONTRIBUTING.md), but let the user run the git commands.

## Commands

Docker must be running for tests and for running the app.

```bash
./mvnw verify                         # build and run all tests, as CI does
./mvnw test -Dtest=PostModuleTests    # run one test class
./mvnw spring-boot:run                # start the app; Docker Compose starts PostgreSQL
```

The frontend, from `frontend/` (no Docker needed):

```bash
npm run dev:mock                      # the app with the mock API, on localhost:5173
npm run dev                           # the app against the backend on localhost:8080
npm run check                         # lint, format check, types, unit and page tests, build
npm run e2e                           # Playwright end-to-end tests, against mock mode
```

## Rules that are easy to break

- **Never edit a migration that is on `main`**, not even to add a comment: Flyway checksums applied migrations, so any change breaks existing databases. Schema changes go in a new `V<n>__<description>.sql`.
- **Modules talk through each other's public service only.** Entities and repositories are package-private, and other modules are referred to by ID. `ModularityTests` fails the build on a forbidden dependency.
- **`common` is never listed in `allowedDependencies`.** It is a shared module (`@Modulithic(sharedModules = "common")` on `BlogApplication`), so every module may use it, and every module test starts it.
- **A module test whose module uses another module** needs `@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)`.
- **Take the time from the injected `Clock`**, never from `Instant.now()`, in production code.
- **Frontend features talk through each other's `index.ts` only,** and only in the directions declared in `allowedFeatureDependencies` in `frontend/eslint.config.js`. Imports that leave a folder use the `@/` alias. ESLint fails on a forbidden import.
- **The mock API follows docs/API.md,** not the backend's current state. When a backend step adds endpoints, the mock and the backend must answer alike.
- **Never render content as HTML** in the frontend: no `dangerouslySetInnerHTML` (a lint rule forbids it).
- **Frontend components never read the clock while rendering:** use `useNow()`, and pass the time to formatting functions.
- **Docker Compose:** the local database belongs to the Compose project `blog`. Don't run `docker compose down -v` there without asking: it deletes the user's local data. Give scratch copies their own folder name, so they get a separate Compose project.

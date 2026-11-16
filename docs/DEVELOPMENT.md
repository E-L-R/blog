# Development Guide

How to build, run and test the project on your own machine, and how to add a feature module. The workflow for branches, commits and pull requests is in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Prerequisites

- **JDK 25.** CI uses Eclipse Temurin.
- **Docker**, running. The tests start PostgreSQL with Testcontainers, and local runs start it with Docker Compose.
- No Maven installation is needed: the Maven wrapper (`./mvnw`) downloads the right version.
- **Node.js 22.22 or newer**, with npm, for the frontend only. `frontend/.nvmrc` names the major version, so `nvm use` in `frontend/` picks it.

## Build and test

```bash
./mvnw verify
```

This compiles the code, runs every test against a real PostgreSQL in Docker, and builds the jar in `target/`. CI runs the same command on every pull request and every push to `main` (see [ci.yml](../.github/workflows/ci.yml)).

To run one test class:

```bash
./mvnw test -Dtest=PostModuleTests
```

One error message in the test output is expected: `duplicate key value violates unique constraint "users_username_lower_key"`. `UserModuleTests.onlyOneOfTwoSimultaneousRegistrationsGetsTheUsername` causes it on purpose: two registrations race for one username, and the test passes because the database refuses the second one.

The test strategy and conventions are in [TESTING.md](TESTING.md).

## Run the app

```bash
./mvnw spring-boot:run
```

On startup:
1. Spring Boot finds `compose.yaml` and runs `docker compose up`, which starts a PostgreSQL 18 container. The database, user and password are all `blog`. Spring Boot connects the app to it, so no database settings are needed.
2. Flyway applies the migrations in `src/main/resources/db/migration` that the database doesn't have yet.
3. Hibernate checks that the entities match the tables (`ddl-auto=validate`), and the app listens on port 8080.

The only endpoint so far is `POST /auth/register`; every other request gets `401`. To register a user:

```bash
curl -i -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"email":"alice@example.com","username":"alice","password":"correct horse"}'
```

The answer is `201` with the new account. Sending the same request again gives `409` with the code `EMAIL_TAKEN` (see [API.md](API.md#errors)).

The log line `Using generated security password` at startup is harmless: the security configuration offers no login that could use that password.

Stop the app with Ctrl+C. Spring Boot then stops the container but keeps it, with its data, so the next start reuses them.

Docker Compose names things after the project folder: the project is called `blog`, and the container `blog-postgres-1`.

### Look inside the local database

While the app is running:

```bash
docker compose exec postgres psql -U blog
```

This opens `psql` inside the container, so you don't need it installed. For example, `\dt` lists the tables, `SELECT * FROM flyway_schema_history;` shows which migrations have run, and `\q` quits.

To connect from IntelliJ or another database tool, use host `localhost`, database `blog`, user `blog`, password `blog`, and the port that this command prints:

```bash
docker compose port postgres 5432
```

The port can change whenever the container starts, because `compose.yaml` publishes port 5432 without choosing the port on your machine.

### Reset the local database

```bash
docker compose down -v
```

This deletes the container **and all its data** (`-v` removes its volume). The next start creates an empty database and runs every migration again. Run it in the project folder, so it only touches the `blog` project.

You need this after changing a migration that your local database has already applied, for example while you are still working on a new one. Flyway stores a checksum of every migration it runs, and refuses to start when a file no longer matches.

## Frontend

The web app is in `frontend/` (see [FRONTEND.md](FRONTEND.md)). Run these commands there:

```bash
cd frontend
```

Install the dependencies once, and again whenever `package-lock.json` changes:

```bash
npm install
```

### Run it with the mock API

```bash
npm run dev:mock
```

Open http://localhost:5173. The app answers its own API calls in the browser ([FRONTEND.md](FRONTEND.md#mock-api)), so neither the backend nor Docker is needed, and every page works, including the ones whose endpoints the backend hasn't built yet. Log in as `demo@example.com` with the password `password123`. The browser console says `[mock API]` when the mock is running.

The mock's data survives reloads. To start over, run `localStorage.removeItem('blog.mockDb')` in the browser console and reload.

### Run it against the backend

Start the backend in one terminal, from the project folder:

```bash
./mvnw spring-boot:run
```

and the frontend in another, from `frontend/`:

```bash
npm run dev
```

Open http://localhost:5173. The dev server forwards every request to `/api/...` to `http://localhost:8080/...`, so the browser only talks to one origin and the backend needs no CORS settings ([D18](DECISIONS.md#d18-the-browser-calls-the-api-on-its-own-origin)). Today only registration works this way: the other pages need endpoints that come with the rest of the `auth` step. Until then, use mock mode.

Port 5173 must be free: the dev server refuses to start on another port, so the address stays predictable.

### Test and check the frontend

```bash
npm test
```

runs the unit, component and page tests (Vitest), in about ten seconds. `npm run test:watch` keeps running and reruns the affected tests on every save.

```bash
npm run check
```

runs everything CI runs except the end-to-end tests: lint (including the feature boundaries), the formatting check, the type check, the tests and the production build. `npm run format` fixes the formatting.

The end-to-end tests drive Chromium through the main flows. Install the browser once:

```bash
npx playwright install chromium
```

then run them:

```bash
npm run e2e
```

Playwright starts the app in mock mode on port 4173 by itself, or reuses one that is already running there. After a failure, `npx playwright show-report` shows what happened, with a trace of each failed test.

### Build for production

```bash
npm run build
```

type-checks the app and writes it to `frontend/dist/`: an `index.html` and hashed files in `assets/`. `npm run preview` serves the build locally. How to serve it in production is in [OPERATIONS.md](OPERATIONS.md#frontend).

## Project layout

```
.
├── src/main/java/io/github/elr/blog/
│   ├── BlogApplication.java              the application class; declares common as a shared module
│   ├── common/                           shared infrastructure (the Clock bean, error handling)
│   ├── user/                             users, the bot user, deleted accounts
│   ├── post/                             posts
│   └── auth/                             registration, the security configuration
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/                     Flyway migrations
├── src/test/java/io/github/elr/blog/
│   ├── ModularityTests.java              module boundaries and diagrams
│   ├── BlogApplicationTests.java         the whole application starts
│   ├── TestcontainersConfiguration.java  the PostgreSQL container for tests
│   ├── user/UserModuleTests.java
│   ├── post/PostModuleTests.java
│   └── auth/AuthModuleTests.java
├── frontend/                             the web app (React, TypeScript, Vite)
│   ├── src/
│   │   ├── main.tsx                      the entry point; starts the mock API in mock mode
│   │   ├── app/                          routes, layout, providers
│   │   ├── api/                          the HTTP client, the token store, the API's types
│   │   ├── lib/                          validation, error messages, time formatting
│   │   ├── components/                   shared UI components
│   │   ├── features/                     auth, posts, account
│   │   ├── styles/                       design tokens and base styles
│   │   ├── mocks/                        the mock API (MSW)
│   │   └── test/                         test setup and helpers
│   ├── e2e/                              Playwright end-to-end tests
│   ├── eslint.config.js                  lint rules, including the feature boundaries
│   └── vite.config.ts                    dev server, proxy, build and Vitest
├── compose.yaml                          PostgreSQL for local runs
├── .github/workflows/ci.yml              CI, for the backend and the frontend
└── docs/                                 design documents and this guide
```

How the modules fit together is in [ARCHITECTURE.md](ARCHITECTURE.md), and how the frontend is built in [FRONTEND.md](FRONTEND.md).

## Module diagrams

Every test run writes PlantUML diagrams of the modules to `target/spring-modulith-docs/` (see [ARCHITECTURE.md](ARCHITECTURE.md#generated-docs)). To view one, open the `.puml` file in a PlantUML viewer, such as the PlantUML Integration plugin for IntelliJ.

## Adding a feature module

These steps are for the backend. The frontend's are in [FRONTEND.md](FRONTEND.md#adding-a-feature).

The `user` and `post` modules were built in these steps. Follow them for `comment`, `like` and `ai`, using the design in [DOMAIN.md](DOMAIN.md) and [DATABASE.md](DATABASE.md):

1. **Migration:** a new `V<next number>__<description>.sql`. Name every constraint and index, and index foreign key columns (see [DATABASE.md](DATABASE.md#conventions)).
2. **Entity:** package-private, with no setters. Its constructor and methods enforce the rules, and it refers to other modules' entities by ID only (see [ARCHITECTURE.md](ARCHITECTURE.md#conventions)).
3. **Repository:** a package-private interface that extends `JpaRepository`.
4. **Read model:** a public record, such as `PostView`, with a package-private `of(entity)` method.
5. **Exceptions:** one public `RuntimeException` subclass per failure. If the API reports the failure, extend the matching base class in `common.error`, such as `ConflictException` (see [ARCHITECTURE.md](ARCHITECTURE.md#error-handling)).
6. **Service:** the module's public entry point. `@Transactional(readOnly = true)` on the class, `@Transactional` on each method that writes, and the time from the `Clock`.
7. **`package-info.java`:** `@ApplicationModule` with a `displayName` and the `allowedDependencies` from the [module table](ARCHITECTURE.md#modules). `common` is never listed.
8. **Endpoints:** a package-private controller in `web/`, with request and response records in `web/dto/`. Mark request bodies `@Valid`, and open public endpoints in `auth`'s `SecurityConfiguration`. The `auth` module shows the pattern.
9. **Module test:** `<Name>ModuleTests`, with `@ApplicationModuleTest` and `@Import(TestcontainersConfiguration.class)`. Add `mode = BootstrapMode.DIRECT_DEPENDENCIES` if the module uses another module, and `@AutoConfigureMockMvc` to test endpoints. Cover the module's rules and its [required tests](TESTING.md#required-tests), and prove the important database constraints with plain SQL through `JdbcTemplate`.
10. **Frontend:** if the step adds endpoints the frontend already uses, check that they answer exactly as the mock API does (`frontend/src/mocks`), and fix whichever of the two differs from [API.md](API.md).
11. **Docs:** update the documents listed in [Keeping the docs current](../CONTRIBUTING.md#keeping-the-docs-current), the README roadmap and the CHANGELOG.
12. **Check:** `./mvnw verify` passes.

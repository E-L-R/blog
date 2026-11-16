# Development Guide

How to build, run and test the project on your own machine, and how to add a feature module. The workflow for branches, commits and pull requests is in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Prerequisites

- **JDK 25.** CI uses Eclipse Temurin.
- **Docker**, running. The tests start PostgreSQL with Testcontainers, and local runs start it with Docker Compose.
- No Maven installation is needed: the Maven wrapper (`./mvnw`) downloads the right version.
- **Node.js 22.22 or newer**, with npm, for the frontend only. `frontend/.nvmrc` names the major version, so `nvm use` in `frontend/` picks it.
- **[Ollama](https://ollama.com/download)**, running, with `llama3.2` pulled, for the AI features in local runs (see [AI models](#ai-models)). The tests don't need it.

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

Set up Ollama first (see [AI models](#ai-models)): install it, and download the model once with `ollama pull llama3.2`. Then:

```bash
./mvnw spring-boot:run
```

This starts the app with the `dev` profile (`src/main/resources/application-dev.properties`), which supplies a development JWT secret and has `llama3.2` on Ollama write the TL;DRs and replies. On startup:
1. Spring Boot finds `compose.yaml` and runs `docker compose up`, which starts a PostgreSQL 18 container. The database, user and password are all `blog`. Spring Boot connects the app to it, so no database settings are needed.
2. Flyway applies the migrations in `src/main/resources/db/migration` that the database doesn't have yet.
3. Hibernate checks that the entities match the tables (`ddl-auto=validate`), and the app listens on port 8080.
4. The log says which model it uses: `AI: Ollama model llama3.2 at http://localhost:11434 writes TL;DRs and replies`.

To try the API, register a user, log in, and send the token with each request that needs one:

```bash
curl -i -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"email":"alice@example.com","username":"alice","password":"correct horse"}'

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"alice@example.com","password":"correct horse"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -i -X POST localhost:8080/posts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"Hello","content":"My first post"}'
curl -s localhost:8080/posts
```

Swagger UI at http://localhost:8080/swagger-ui.html does the same from the browser: log in there, then click **Authorize** and paste the access token.

Registering answers `201` with the new account; sending the same request again gives `409` with the code `EMAIL_TAKEN`. Creating a post answers `201` with its URL in the `Location` header. The token is valid for an hour. Every endpoint is described in [API.md](API.md).

To run the app without the `dev` profile, as in production, set the secret yourself: `BLOG_AUTH_JWT_SECRET=$(openssl rand -base64 32)`. See [OPERATIONS.md](OPERATIONS.md#configuration).

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

## AI models

The AI features need a model. The backend can use three:

| Model | When | Needs |
|---|---|---|
| **A local model on [Ollama](https://ollama.com)** | Local runs, with the `dev` profile: the default | Ollama, and `llama3.2` pulled once |
| **The fake LLM** | The tests and CI; local runs without Ollama | Nothing. It answers at once, always with the same text. |
| **Claude** | Production; local runs with an API key | An Anthropic API key |

The [web app's mock mode](#run-it-with-the-mock-api) needs none of them: the browser answers by itself.

### Ollama

1. Install Ollama from https://ollama.com/download. On macOS and Windows, the app runs the Ollama server in the background. On Linux, the install script sets it up as a service; otherwise start it with `ollama serve`.

   Or run it in Docker instead, if you'd rather not install it. It is slower there on macOS, where containers can't use the graphics card:

   ```bash
   docker run -d --name ollama -p 11434:11434 -v ollama:/root/.ollama ollama/ollama
   ```

2. Download the model, about 2 GB. `llama3.2` (3 billion parameters) runs on an ordinary laptop, even without a graphics card:

   ```bash
   ollama pull llama3.2
   ```

   With Docker, run it inside the container: `docker exec ollama ollama pull llama3.2`.

3. Check that the server answers and has the model:

   ```bash
   curl http://localhost:11434/api/tags
   ```

   The answer lists the models you pulled.

4. Start the backend with `./mvnw spring-boot:run`, and the web app with `npm run dev` in `frontend/` (see [Run it against the backend](#run-it-against-the-backend)). **Not `npm run dev:mock`**: in mock mode the browser answers by itself, and Ollama is never asked.

5. Write a post. Its TL;DR appears once the model has answered, and "Ask AI" on a post or comment gets a reply from the bot. The first call takes longest, because Ollama loads the model into memory first.

### Settings

The `dev` profile sets the AI up for a laptop:

| Setting | In `dev` | Why |
|---|---|---|
| `blog.ai.provider` | `ollama` | A local model, no API key |
| `blog.ai.llm.model` | `llama3.2` | Small enough for a laptop |
| `blog.ai.worker.concurrency` | `1` | Ollama answers one call at a time; more would only wait in its queue |
| `blog.ai.llm.timeout` | `180s` | A local model is slow, especially on the first call, while it loads |
| `blog.ai.worker.lease` | `5m` | Must stay longer than the timeout, or a second worker could take over a running call |

A call that fails is tried again, up to five times, after 30 seconds, then 1, 2 and 4 minutes. [OPERATIONS.md](OPERATIONS.md#configuration) lists every setting.

To change one for a single run, pass it as an argument. For example, a larger model, which writes better text but needs more memory and time (pull it first):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--blog.ai.llm.model=qwen3:8b
```

Several arguments are separated by spaces, inside quotes: `-Dspring-boot.run.arguments="--blog.ai.llm.model=qwen3:8b --blog.ai.llm.timeout=300s --blog.ai.worker.lease=8m"`.

- **On Windows**, in PowerShell, quote the whole argument, or PowerShell splits it at the dots: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--blog.ai.llm.model=qwen3:8b"`.
- **Environment variables** work too, and also take precedence over the `dev` profile: `BLOG_AI_LLM_MODEL=qwen3:8b ./mvnw spring-boot:run`.
- **Another address.** If Ollama runs on another machine or port, set `blog.ai.ollama.base-url`, for example `--blog.ai.ollama.base-url=http://192.168.1.20:11434`.
- **Thinking models**, such as `qwen3`, may write their reasoning between `<think>` tags; it is left out of the TL;DR or reply.

### Without Ollama: the fake LLM

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--blog.ai.fake=true
```

TL;DRs and AI replies then appear at once, always with the same text: "This is an answer from the fake LLM." The tests and CI use it too.

### Claude

```bash
ANTHROPIC_API_KEY=<your key> ./mvnw spring-boot:run -Dspring-boot.run.arguments="--blog.ai.provider=anthropic --blog.ai.llm.model=claude-opus-5-5"
```

The model must be set as well: the `dev` profile's `llama3.2` is an Ollama model. Production uses Claude by default ([OPERATIONS.md](OPERATIONS.md#configuration)).

### When the model doesn't answer

If a TL;DR stays at "Writing a summary…", or "Ask AI" says the AI couldn't reply, the backend's log says why. Every failed call logs a warning with the AI request's ID, whether it will be tried again, and the reason:

```
WARN … AiJobFinisher : AI request 01a1e662-… (TLDR) attempt 1 failed, trying again at 2026-11-16T22:03:25Z: Ollama could not be reached at http://localhost:11434: nothing answered there. Is Ollama running, and is blog.ai.ollama.base-url right?
WARN … AiJobFinisher : AI request 01a1e662-… (TLDR) failed for good after 1 attempt(s): Ollama answered HTTP 404: {"error":"model 'mistral' not found"}
```

| Symptom or message | Meaning | Fix |
|---|---|---|
| The application doesn't start: `blog.ai.provider is ollama, but blog.ai.llm.model is claude-…` | The model is still Claude's default | Add `--blog.ai.llm.model=<the model you pulled>` |
| At startup, `AI: the fake LLM …` or `AI: Claude model …` | The `dev` profile's Ollama settings were overridden, by arguments or by `BLOG_AI_*` environment variables | Start without them, or check what they set |
| `ANTHROPIC_API_KEY is not set` at startup | Claude is being used, not Ollama | As the line above |
| The bot's replies say "Thanks for asking. In short: …" | The frontend runs in mock mode | Use `npm run dev`, not `npm run dev:mock` |
| `Ollama answered HTTP 404: … not found` | The model was never pulled, or its name differs | `ollama list` shows the exact names; `ollama pull <model>`, or set the model to a listed name |
| `Ollama could not be reached …: nothing answered there` | The server isn't running, or runs at another address | Start Ollama (`ollama serve`, or the app), or set `blog.ai.ollama.base-url`. The worker tries again by itself. |
| `Ollama could not be reached …: no answer within 180 seconds` | The model is too slow for the timeout | Use a smaller model, or raise `blog.ai.llm.timeout` together with `blog.ai.worker.lease` (see [Settings](#settings)) |
| `The answer was cut off at the output token limit` | The model wrote more than `blog.ai.llm.max-output-tokens` (4096) | Rare with the default; raise the limit, or try another model |

The same reason is kept in the `error` column of the `ai_requests` table, so the latest ones can also be read from the database (see [Look inside the local database](#look-inside-the-local-database)):

```sql
SELECT type, status, attempts, error FROM ai_requests ORDER BY created_at DESC LIMIT 5;
```

A TL;DR or reply that failed for good shows as failed in the app. Editing the post's content asks for a new TL;DR, and "Ask AI" can be used again.

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

Open http://localhost:5173. The app answers its own API calls in the browser ([FRONTEND.md](FRONTEND.md#mock-api)), so neither the backend nor Docker is needed. Log in as `demo@example.com` with the password `password123`. The browser console says `[mock API]` when the mock is running.

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

Open http://localhost:5173. The dev server forwards every request to `/api/...` to `http://localhost:8080/...`, so the browser only talks to one origin and the backend needs no CORS settings ([D19](DECISIONS.md#d19-the-browser-calls-the-api-on-its-own-origin)). Every page works this way, and the TL;DRs and AI replies come from the model the backend uses: `llama3.2` on Ollama with the `dev` profile (see [AI models](#ai-models)).

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

The same tests run against the real backend. Start the backend first, from the project folder, with the fake LLM, which answers at once (CI does the same; a local model may be too slow for the tests' waits):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--blog.ai.fake=true
```

Then, in `frontend/`:

```bash
npm run e2e:backend
```

Playwright starts the frontend's dev server on port 4174, which forwards `/api` to the backend on port 8080. The tests create their own users and posts, with names no other run uses, so they can run any number of times against the same database.

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
│   ├── common/                           shared infrastructure (the Clock bean, error handling, web helpers)
│   ├── user/                             users, the bot user, deleted accounts, public profiles
│   ├── post/                             posts and their endpoints
│   ├── comment/                          comment threads
│   ├── auth/                             registration, login, account deletion, the security configuration
│   ├── like/                             likes on posts and comments
│   └── ai/                               the AI job queue, worker, prompts and LLM clients
├── src/main/resources/
│   ├── application.properties            defaults for every environment
│   ├── application-dev.properties        the dev profile, for ./mvnw spring-boot:run
│   └── db/migration/                     Flyway migrations
├── src/test/java/io/github/elr/blog/
│   ├── ModularityTests.java              module boundaries and diagrams
│   ├── BlogApplicationTests.java         the whole application starts
│   ├── TestcontainersConfiguration.java  the PostgreSQL container for tests
│   ├── common/                           the error advice, without Spring
│   ├── user/                             UserModuleTests, UserApiTests
│   ├── post/                             PostModuleTests, PostApiTests
│   ├── comment/                          CommentModuleTests, CommentApiTests
│   ├── like/                             LikeModuleTests, LikeApiTests
│   ├── ai/                               AiModuleTests, AiApiTests, PromptBuilderTests, AiPropertiesTests
│   └── auth/                             AuthModuleTests, JwtSecretTests
├── src/test/resources/
│   └── application-test.properties       the test profile
├── frontend/                             the web app (React, TypeScript, Vite)
│   ├── src/
│   │   ├── main.tsx                      the entry point; starts the mock API in mock mode
│   │   ├── app/                          routes, layout, providers
│   │   ├── api/                          the HTTP client, the token store, the API's types
│   │   ├── lib/                          validation, error messages, time formatting
│   │   ├── components/                   shared UI components
│   │   ├── features/                     auth, posts, comments, likes, ai, account
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

Every module was built in these steps. Follow them for a new one, using the design in [DOMAIN.md](DOMAIN.md) and [DATABASE.md](DATABASE.md):

1. **Migration:** a new `V<next number>__<description>.sql`. Name every constraint and index, and index foreign key columns (see [DATABASE.md](DATABASE.md#conventions)).
2. **Entity:** package-private, with no setters. Its constructor and methods enforce the rules, and it refers to other modules' entities by ID only (see [ARCHITECTURE.md](ARCHITECTURE.md#conventions)).
3. **Repository:** a package-private interface that extends `JpaRepository`.
4. **Read model:** a public record, such as `PostView`, with a package-private `of(entity)` method.
5. **Exceptions:** one public `RuntimeException` subclass per failure. If the API reports the failure, extend the matching base class in `common.error`, such as `ConflictException` (see [ARCHITECTURE.md](ARCHITECTURE.md#error-handling)).
6. **Service:** the module's public entry point. `@Transactional(readOnly = true)` on the class, `@Transactional` on each method that writes, and the time from the `Clock`.
7. **`package-info.java`:** `@ApplicationModule` with a `displayName` and the `allowedDependencies` from the [module table](ARCHITECTURE.md#modules). `common` is never listed.
8. **Endpoints:** a package-private controller in `web/`, with request and response records in `web/dto/`, an `@Tag` on the controller and an `@Operation(summary = ...)` on each endpoint. Mark request bodies `@Valid`, take the caller's ID from a `@CurrentUser UUID` parameter, and return lists as `PageResponse`. `GET` endpoints are public and everything else needs a token; a different rule goes in `auth`'s `SecurityConfiguration`. The `post` module shows the pattern.
9. **Module test:** `<Name>ModuleTests`, with `@ApplicationModuleTest`, `@Import(TestcontainersConfiguration.class)` and `@ActiveProfiles("test")`. Add `mode = BootstrapMode.DIRECT_DEPENDENCIES` if the module uses another module, or `ALL_DEPENDENCIES` if those modules use others in turn (as `like` does). To test endpoints, add `extraIncludes = "auth"` (for the security configuration) and `@AutoConfigureMockMvc`, and put the HTTP tests in `<Name>ApiTests` with the same annotations, so both classes share one application context. Cover the module's rules and its [required tests](TESTING.md#required-tests), and prove the important database constraints with plain SQL through `JdbcTemplate`.
10. **Frontend:** if the frontend uses the new endpoints, check that they answer exactly as its mock API does (`frontend/src/mocks`), and fix whichever of the two differs from [API.md](API.md).
11. **Docs:** update the documents listed in [Keeping the docs current](../CONTRIBUTING.md#keeping-the-docs-current), the README roadmap and the CHANGELOG.
12. **Check:** `./mvnw verify` passes.

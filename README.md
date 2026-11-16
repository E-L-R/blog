# Blog API

A REST backend for a blogging platform with AI features, built with Spring Boot as a **modular monolith**, and a React web app for it.

Users write posts, comment, reply to comments, and like posts and comments. An AI model writes a **TL;DR** for every post, and users can **ask the AI to reply** to a post or comment. Its answer appears as a regular comment, written by a bot user.

> **Status: feature-complete, not released.** Every step of the [Roadmap](#roadmap) is built and tested: accounts with JWT login, posts, threaded comments, likes, the AI features (TL;DRs and bot replies) and the OpenAPI description. Hosting is not chosen yet. The React web app covers all of it.

## Getting started

You need JDK 25 and Docker (running).

Run all the tests, as CI does:

```bash
./mvnw verify
```

Start the app:

```bash
./mvnw spring-boot:run
```

Spring Boot starts PostgreSQL in Docker from `compose.yaml`, Flyway creates the tables, and the app listens on port 8080 with the `dev` profile. Swagger UI at http://localhost:8080/swagger-ui.html lists every endpoint and lets you try them. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) shows how to register, log in and call the API with `curl`, how to look inside the database, and how to reset it. The endpoints are described in [docs/API.md](docs/API.md).

The web app needs Node.js 22.22 or newer. To try it without the backend, with a mock API in the browser:

```bash
cd frontend
npm install
npm run dev:mock
```

Then open http://localhost:5173 and log in as `demo@example.com` with `password123`. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#frontend) explains how to run it against the backend, and how to test it.

## Documentation

| Document | Contents |
|---|---|
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Building, running and testing on your machine; adding a module |
| [docs/FRONTEND.md](docs/FRONTEND.md) | The web app: features and their boundaries, pages, data fetching, the mock API, styling, conventions |
| [docs/DOMAIN.md](docs/DOMAIN.md) | Entities, rules, the AI job state machine, and the main flows |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules and their dependency rules, events, the AI worker, security, error handling, conventions |
| [docs/DATABASE.md](docs/DATABASE.md) | Tables, constraints, indexes, migrations, and the statements written in SQL |
| [docs/API.md](docs/API.md) | REST endpoints, resources, errors and validation |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy, the current tests, and the tests each feature needs, for the backend and the frontend |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Configuration, profiles, monitoring, failure modes |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Why the main choices were made, and what is still open |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Workflow, commit messages, definition of done |
| [SECURITY.md](SECURITY.md) | Reporting vulnerabilities, the security model, known limitations |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes |

The design documents describe the whole application. The few parts that are not built yet, such as structured logging, are marked *planned*.

## Features

| Feature | Status |
|---|---|
| **Web app:** a fast, accessible React app for reading, writing and discussing, with a light and a dark theme | Built: accounts, posts with their TL;DR, comment threads, likes and AI replies |
| **Accounts:** register, log in, and delete your account. As on Reddit, your posts and comments stay, shown as "[deleted]", and your username is retired for good. | Built |
| **Posts:** create, edit, delete, and list newest first | Built |
| **Threaded comments:** comment on posts and reply to comments, with soft delete so threads stay intact | Built |
| **Likes** on posts and comments, one per user, enforced by the database | Built |
| **AI TL;DR:** generated in the background when a post is created, and regenerated when its content changes | Built |
| **AI replies:** a user asks the AI to reply to a post or comment, and a bot user posts the answer as a comment | Built |

## Design highlights

- **Modular monolith with Spring Modulith.** The code is split into feature modules. A test checks the module boundaries, so a forbidden dependency fails the build. Modules refer to each other's entities by ID, while the database keeps real foreign keys.
- **The database guards the rules too.** Constraints back up the Java code: usernames are unique ignoring case, only active regular accounts have a password, and the bot can't be deleted.
- **Rich domain model.** Business rules live on the entities (`post.edit()`, `user.delete()`, `comment.softDelete()`, `aiRequest.claim()`), and services own the transactions.
- **Safe against races.** Two simultaneous edits of a post can't overwrite each other: the second one fails (optimistic locking). Two simultaneous registrations can't both get the same username: the database's unique index refuses the second one, and the API answers `409`. Likes use `INSERT ... ON CONFLICT DO NOTHING`, so a double click can't count twice, and like counts are updated by SQL and read-only in JPA, so saving an entity can't overwrite them. A TL;DR is applied only if the post's content hash still matches what the model saw.
- **Event-driven decoupling.** `post` knows nothing about AI. It publishes `PostCreatedEvent` and `PostContentChangedEvent`, and the `ai` module queues a TL;DR job in the same transaction. The LLM call happens later in a worker, so an AI failure can never roll back a post.
- **Database-backed job queue.** Each LLM call is an `AiRequest` row. A scheduled worker claims jobs with `SELECT ... FOR UPDATE SKIP LOCKED` and a lease, calls the model outside any transaction, and retries with backoff. It is safe across several instances and keeps an audit trail without a message broker.
- **A frontend tested against the API design.** The React app's tests run against a mock API that implements [docs/API.md](docs/API.md), so they need neither Docker nor the backend. Its features have checked boundaries, like the backend's modules.

## Tech stack

| Concern | Technology |
|---|---|
| Language / runtime | Java 25, Spring Boot 4.1 |
| Modularity | Spring Modulith |
| Persistence | PostgreSQL 18, Spring Data JPA, Flyway |
| Security | Spring Security, JWT (OAuth2 resource server) |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| AI | Claude, through the official Anthropic Java SDK; or a local model through Ollama |
| API docs | springdoc-openapi (Swagger UI) |
| Monitoring | Spring Boot Actuator, Micrometer |
| Testing | JUnit 5, AssertJ, Testcontainers, Spring Modulith test |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, CSS Modules |
| Frontend testing | Vitest, Testing Library, Mock Service Worker, Playwright |
| Dev / CI | Docker Compose, GitHub Actions |

[ARCHITECTURE.md](docs/ARCHITECTURE.md#tech-stack) shows which of these are in use already.

## Project structure

```
src/main/java/io/github/elr/blog
├── common/    shared infrastructure: the Clock bean, error handling, the current user, pages
├── user/      users, the AI bot user, deleted accounts
├── post/      posts and their TL;DR
├── auth/      registration, JWT login, account deletion and the security configuration
├── comment/   comment threads, soft delete, the bot's replies
├── like/      post and comment likes
└── ai/        AiRequest queue, worker, prompts, LLM client

frontend/src
├── app/         routes, layout, providers
├── api/         the HTTP client, the token store, the API's types
├── lib/         validation, error messages, time formatting
├── components/  shared UI components
├── features/    auth, posts, comments, likes, ai, account
└── mocks/       the mock API, for development and tests
```

## Out of scope (for now)

- Email verification
- Admin roles and moderation
- Refresh tokens and logout (access tokens are short-lived)
- Paginating comment threads (a thread is returned whole)
- Streaming AI responses
- Editing or deleting the bot's comments

[SECURITY.md](SECURITY.md#known-limitations) lists the security limitations that follow from these.

## Roadmap

- [x] Design documents
- [x] Spring Modulith setup and the module verification test
- [x] PostgreSQL with Flyway; Docker Compose for local runs and Testcontainers for tests
- [x] GitHub Actions CI
- [x] `user` module: the users table, the bot user, the account deletion rule
- [x] `post` module: create, edit, delete and list posts, with optimistic locking
- [x] `auth` module: registration, JWT login, account deletion and the security configuration, plus the first endpoints (users and posts) with error handling
  - [x] Registration (`POST /auth/register`), with validation, the security configuration and the first error responses
  - [x] Login with a JWT, and checking tokens on later requests
  - [x] Post endpoints
  - [x] Account endpoints (`GET /me`, `GET /users/{id}`, `POST /me/delete`)
- [x] `comment` module
- [x] `like` module
- [x] `ai` module: job queue and worker, TL;DR generation, AI replies
- [x] OpenAPI docs
- [x] Web app (`frontend/`): React and TypeScript, with a mock API built from the design, page tests and end-to-end tests
  - [x] Accounts: register, log in, log out, account page, account deletion, profiles
  - [x] Posts: list, read, write, edit, delete, with the TL;DR
  - [x] Comments, likes and AI replies
  - [x] End-to-end tests against the real backend, in CI

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and, for working on the code, [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Security issues: see [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE)

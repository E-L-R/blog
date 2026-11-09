# Blog API

A REST backend for a blogging platform with AI features, built with Spring Boot as a **modular monolith**.

Users will be able to write posts, comment, reply to comments, and like posts and comments. An AI model will write a **TL;DR** for every post, and users will be able to **ask the AI to reply** to a post or comment. Its answer appears as a regular comment.

> **Status: early development.** The module structure, the database, and the business logic of the `user` and `post` modules are built and tested. There are no HTTP endpoints yet: they start with the `auth` step. See the [Roadmap](#roadmap).

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

Spring Boot starts PostgreSQL in Docker from `compose.yaml`, Flyway creates the tables, and the app listens on port 8080. There are no endpoints yet, so every request gets `401`. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) has the details, including how to look inside the database and how to reset it.

## Documentation

| Document | Contents |
|---|---|
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Building, running and testing on your machine; adding a module |
| [docs/DOMAIN.md](docs/DOMAIN.md) | Entities, rules, the AI job state machine, and the main flows |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules and their dependency rules, events, the AI worker, security, error handling, conventions |
| [docs/DATABASE.md](docs/DATABASE.md) | Tables, constraints, indexes, migrations, and the statements written in SQL |
| [docs/API.md](docs/API.md) | REST endpoints, resources, errors and validation |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy, the current tests, and the tests each feature needs |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Configuration, profiles, monitoring, failure modes |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Why the main choices were made, and what is still open |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Workflow, commit messages, definition of done |
| [SECURITY.md](SECURITY.md) | Reporting vulnerabilities, the security model, known limitations |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes |

The design documents describe the whole application. Parts that are not built yet are marked *planned*.

## Features

| Feature | Status |
|---|---|
| **Accounts:** register, log in, and delete your account. As on Reddit, your posts and comments stay, shown as "[deleted]", and your username is retired for good. | Users and the deletion rule are built. Registration, login and the endpoints come with `auth`. |
| **Posts:** create, edit, delete, and list newest first | Built, without endpoints yet |
| **Threaded comments:** comment on posts and reply to comments, with soft delete so threads stay intact | Planned |
| **Likes** on posts and comments, one per user, enforced by the database | Planned |
| **AI TL;DR:** generated in the background when a post is created, and regenerated when its content changes | Planned |
| **AI replies:** a user asks the AI to reply to a post or comment, and a bot user posts the answer as a comment | Planned. The bot user already exists. |

## Design highlights

- **Modular monolith with Spring Modulith.** The code is split into feature modules. A test checks the module boundaries, so a forbidden dependency fails the build. Modules refer to each other's entities by ID, while the database keeps real foreign keys.
- **The database guards the rules too.** Constraints back up the Java code: usernames are unique ignoring case, only active regular accounts have a password, and the bot can't be deleted.
- **Rich domain model.** Business rules live on the entities (`post.edit()`, `user.delete()`, later `comment.softDelete()` and `aiRequest.claim()`), and services own the transactions.
- **Safe against races.** Two simultaneous edits of a post can't overwrite each other: the second one fails (optimistic locking). *Planned:* a TL;DR is applied only if the post's content hash still matches what the model saw; likes use `INSERT ... ON CONFLICT DO NOTHING`, so a double click can't count twice; and columns updated by SQL are read-only in JPA, so saving an entity can't overwrite them.
- **Event-driven decoupling** *(planned)*. `post` will know nothing about AI. It publishes `PostCreatedEvent` and `PostContentChangedEvent`, and the `ai` module queues a TL;DR job in the same transaction. The LLM call happens later in a worker, so an AI failure can never roll back a post.
- **Database-backed job queue** *(planned)*. Each LLM call is an `AiRequest` row. A scheduled worker claims jobs with `SELECT ... FOR UPDATE SKIP LOCKED` and a lease, calls the model outside any transaction, and retries with backoff. It is safe across several instances and keeps an audit trail without a message broker.

## Tech stack

| Concern | Technology |
|---|---|
| Language / runtime | Java 25, Spring Boot 4.1 |
| Modularity | Spring Modulith |
| Persistence | PostgreSQL 18, Spring Data JPA, Flyway |
| Security | Spring Security, JWT (OAuth2 resource server) |
| AI | Spring AI (provider not chosen yet) |
| API docs | springdoc-openapi (Swagger UI) |
| Monitoring | Spring Boot Actuator, Micrometer |
| Testing | JUnit 5, AssertJ, Testcontainers, Spring Modulith test |
| Dev / CI | Docker Compose, GitHub Actions |

[ARCHITECTURE.md](docs/ARCHITECTURE.md#tech-stack) shows which of these are in use already.

## Project structure

```
src/main/java/io/github/elr/blog
├── common/    shared infrastructure: the Clock bean; later error handling and the current user
├── user/      users, the AI bot user, deleted accounts
├── post/      posts; later their TL;DR
├── auth/      planned: registration, login, account deletion, JWT, security configuration
├── comment/   planned: comment threads
├── like/      planned: post and comment likes
└── ai/        planned: AiRequest queue, worker, prompts, LLM client
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
- [ ] `auth` module: registration, JWT login, account deletion and the security configuration, plus the first endpoints (users and posts) with error handling
- [ ] `comment` module
- [ ] `like` module
- [ ] `ai` module: job queue and worker, TL;DR generation, AI replies
- [ ] OpenAPI docs

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and, for working on the code, [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Security issues: see [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE)

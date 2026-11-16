# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project will use [Semantic Versioning](https://semver.org/spec/v2.0.0.html) from its first release.

## [Unreleased]

### Added
- Spring Boot 4.1 project on Java 25, generated with Spring Initializr.
- Spring Modulith, with a test that checks the module boundaries and writes module diagrams ([#1](https://github.com/E-L-R/blog/pull/1)).
- PostgreSQL 18 with Flyway migrations, started by Docker Compose for local runs and by Testcontainers for tests ([#3](https://github.com/E-L-R/blog/pull/3)).
- GitHub Actions CI, which runs `./mvnw verify` on every pull request and every push to `main` ([#4](https://github.com/E-L-R/blog/pull/4)).
- `user` module: the `users` table, the bot user, and the rule for deleting an account. No endpoints yet ([#5](https://github.com/E-L-R/blog/pull/5)).
- `post` module: creating, editing, deleting and listing posts, with optimistic locking. No endpoints yet ([#6](https://github.com/E-L-R/blog/pull/6)).
- `auth` module, first part: `POST /auth/register` with input validation and bcrypt password hashing, and the security configuration. Every other request gets `401` until login is built ([#9](https://github.com/E-L-R/blog/pull/9)).
- Error responses as Problem Details (RFC 9457) with a `code` field: `VALIDATION_FAILED`, `EMAIL_TAKEN`, `USERNAME_TAKEN` and `INTERNAL_ERROR` ([#9](https://github.com/E-L-R/blog/pull/9)).
- `auth` module, finished: `POST /auth/login` returns a JWT access token (HS256, one hour by default), checked on every later request; the token of a deleted account stops working at once. `GET /me` and `POST /me/delete` (the password is required). `401` responses are Problem Details with the code `UNAUTHENTICATED` or `INVALID_CREDENTIALS`.
- `GET /users/{id}`: a user's public profile.
- Post endpoints: `GET /posts` (newest first, paged, optionally by author), `GET /posts/{id}`, `POST /posts`, `PUT /posts/{id}`, `DELETE /posts/{id}`, with the error codes `NOT_FOUND`, `NOT_AUTHOR` and `EDIT_CONFLICT`.
- `comment` module: comment threads with replies (`GET` and `POST /posts/{id}/comments`, `GET`, `PUT` and `DELETE /comments/{id}`), soft delete that keeps replies attached, the error code `TARGET_DELETED`, and the bot's replies for the coming AI features.
- `like` module: liking and unliking posts and comments (`PUT` and `DELETE /posts/{id}/like` and `/comments/{id}/like`), idempotent and safe against double clicks, with `likeCount` on posts and comments, and the liked-state lookups `GET /me/likes/posts` and `/me/likes/comments`.
- `ai` module: a TL;DR for every post, generated in the background and regenerated when the content changes (`tldr` and `tldrStatus` on posts), and replies by the bot user (`POST /posts/{id}/ai-reply`, `POST /comments/{id}/ai-reply`, `GET /ai-requests/{id}`). Jobs are rows in `ai_requests`, claimed by a scheduled worker with `FOR UPDATE SKIP LOCKED` and a lease, and retried with backoff. Claude is called through the official Anthropic Java SDK; a fake LLM serves the tests and the `dev` profile.
- Spring Boot Actuator: `/actuator/health` with liveness and readiness, and the AI metrics `blog.ai.queue.size`, `blog.ai.requests.finished` and `blog.ai.llm.duration`.
- The OpenAPI description at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` (springdoc), with the endpoints that need a token marked.
- The `dev` profile, turned on by `./mvnw spring-boot:run`, and CORS for the origins in `blog.cors.allowed-origins`.
- Documentation: README, design documents, development guide, contributing guide, security policy, pull request template and license.
- Web app in `frontend/`: React 19, TypeScript and Vite. Pages to register, log in and out, list, read, write, edit and delete posts with their AI TL;DR, see profiles, and delete an account. It is built against the API design; `npm run dev` runs it against the backend, and `npm run dev:mock` against a mock API in the browser that implements the design.
- Frontend tests: unit and page tests with Vitest, Testing Library and Mock Service Worker, end-to-end tests with Playwright, and ESLint rules that check the boundaries between the frontend's features. CI runs them in a second job.
- The frontend's end-to-end tests also run against the real backend (`npm run e2e:backend`), in a third CI job with PostgreSQL.
- Documentation for the frontend: [docs/FRONTEND.md](docs/FRONTEND.md), and decisions D18 to D22.

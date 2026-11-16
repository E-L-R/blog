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
- The `dev` profile, turned on by `./mvnw spring-boot:run`, and CORS for the origins in `blog.cors.allowed-origins`.
- Documentation: README, design documents, development guide, contributing guide, security policy, pull request template and license.

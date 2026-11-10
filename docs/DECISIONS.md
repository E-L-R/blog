# Design Decisions

A short record of the main design decisions: what was chosen, why, and what it costs. The details live in the linked documents. When a decision changes, update its entry instead of deleting it, and say what replaced it.

All entries are **accepted** unless marked otherwise.

## D1. Modular monolith with Spring Modulith

- **Decision:** one deployable application, split into feature modules whose boundaries are checked by a test. See [ARCHITECTURE.md](ARCHITECTURE.md#modules).
- **Why:** the features are small and share one database. Microservices would add network calls, distributed transactions and deployment work without any benefit at this size. Checked module boundaries keep the code as separable as services would be.
- **Alternatives:** a plain layered application (no enforced boundaries); microservices.
- **Cost:** some cross-module features need extra endpoints (see D13).

## D2. Modules refer to each other's entities by ID

- **Decision:** JPA relationships only inside a module; across modules, a UUID field plus a real foreign key in the schema. See [DOMAIN.md](DOMAIN.md#design-principles).
- **Why:** no module can reach into another's entities or trigger lazy loading across a boundary, and the database still guarantees integrity.
- **Cost:** no navigation between entities of different modules; Hibernate cannot order inserts by these keys, so the referenced row must be saved first (see [DATABASE.md](DATABASE.md#foreign-keys-between-modules)).

## D3. The job queue is a database table

- **Decision:** every LLM call is an `ai_requests` row, claimed with `FOR UPDATE SKIP LOCKED` and a lease. See [ARCHITECTURE.md](ARCHITECTURE.md#async-ai-processing).
- **Why:** no extra infrastructure, jobs are created in the same transaction as the data that caused them, and the table doubles as the audit trail. Safe with several instances.
- **Alternatives:** a message broker (RabbitMQ, Kafka, SQS); Spring's `@Async` (jobs lost on restart, no retries or audit trail).
- **Cost:** polling, and a throughput far below a broker's. More than enough for this application.

## D4. Event listeners run inside the publishing transaction

- **Decision:** `ai` listens to post events with synchronous `@EventListener`s that only insert a job row. See [ARCHITECTURE.md](ARCHITECTURE.md#cross-module-communication-events).
- **Why:** the post and its job commit together, so no event can be lost, without needing Spring Modulith's event publication registry. The slow, failure-prone part (the LLM call) still happens outside the transaction.
- **Alternatives:** `@ApplicationModuleListener` (asynchronous, after commit) with the event publication registry.
- **Cost:** a failure to insert the job also fails the post. Acceptable, because that would be a database error.

## D5. TL;DR freshness is decided by a content hash

- **Decision:** a TL;DR is applied only if the post's current content hash equals the hash of the content the model saw, checked inside the `UPDATE` statement. The TL;DR status is derived from hashes, not stored. Older TL;DR jobs are cancelled when a newer one exists. See [DOMAIN.md](DOMAIN.md#post).
- **Why:** edits can happen while a TL;DR is being generated. The hash makes a stale result harmless without locks, and a derived status cannot get out of sync.
- **Alternatives:** a stored status column (can disagree with the content); locking the post during generation (holds a lock across a slow call).
- **Cost:** a SHA-256 per content change, and a few extra columns.

## D6. One writer per column; optimistic locking for user edits

- **Decision:** columns updated by SQL statements (like counts, TL;DR columns) are read-only in JPA. Posts and comments have a `@Version` column. See [DATABASE.md](DATABASE.md#columns-written-by-sql).
- **Why:** Hibernate writes every mapped column when it saves an entity, so mixing JPA saves and SQL updates on the same column loses updates. Versioning turns simultaneous edits into a `409` instead of a silent overwrite.
- **Alternatives:** Hibernate's `@DynamicUpdate` (only changed columns are written; subtler and Hibernate-specific); versioning every update (likes would cause edit conflicts).
- **Cost:** a loaded entity can hold a stale like count within a transaction; re-read it when needed.

## D7. Likes are rows; counts are caches

- **Decision:** separate `post_likes` and `comment_likes` tables with real foreign keys and a unique constraint; `like_count` is updated with `like_count ± 1` only when a row was actually inserted or deleted. See [DOMAIN.md](DOMAIN.md#flows).
- **Why:** the database enforces one like per user, the counts are cheap to read, and liking is idempotent.
- **Alternatives:** one polymorphic `likes` table (no real foreign key); counting on every read (slow on popular posts); catching the duplicate-key error (aborts the transaction in PostgreSQL).
- **Cost:** two similar tables; counts can drift only through a bug, and can be recomputed.

## D8. Deleting: posts hard, comments soft, AI requests kept

- **Decision:** deleting a post cascades to its comments and likes; deleting a comment only blanks it; AI requests are never deleted, and their references are set to null. See [DOMAIN.md](DOMAIN.md#flows).
- **Why:** the `post` module stays unaware of the other modules; threads keep their shape when a comment goes; the AI audit trail survives deletes.
- **Alternatives:** soft-deleting posts (every query must filter them out); a `PostDeletedEvent` handled by each module.
- **Cost:** deleting a post also removes other users' comments on it.

## D9. AI use is not limited

- **Decision:** users may ask for as many AI replies as they like, and every post gets a TL;DR. This **replaces** an earlier decision to allow each user a limited number of AI replies per rolling 24 hours, counted in the database under an advisory lock.
- **Why:** restricting AI use is not a goal of the project. Dropping the limit removes a lock, a count query, an error case and a setting, and nothing has to survive deletes just to keep the count honest.
- **Alternatives:** the rolling database count above; an in-memory limiter such as Bucket4j (per instance only); Redis (extra infrastructure).
- **Cost:** any user can cause any number of LLM calls. The safety net is a spending limit at the provider (see [OPERATIONS.md](OPERATIONS.md#cost)).

## D10. Self-issued JWTs (HS256), access tokens only

- **Decision:** the app issues and verifies its own short-lived access tokens, signed with a shared secret. Each request with a token also checks that its account is not deleted. No refresh tokens or logout for now. The security configuration lives in `auth`. See [SECURITY.md](../SECURITY.md).
- **Why:** simple for one service that both issues and checks tokens, and it works across instances without shared session state. The account check makes deleting an account take effect at once.
- **Alternatives:** server sessions (state per instance or a shared store); an external identity provider (more setup than this project needs); RS256 (only useful when other services must verify tokens); skipping the account check (a deleted account's token would keep working until it expires).
- **Cost:** one primary-key lookup per request with a token. A stolen token stays valid until it expires, unless the account is deleted; users log in again when it does.

## D11. Errors are Problem Details with a stable `code`

- **Decision:** every error response follows RFC 9457, plus a `code` field. See [API.md](API.md#errors).
- **Why:** a standard format that Spring supports out of the box; the `code` gives clients something stable to branch on.
- **How:** one advice in `common` writes every error. `common` defines a base exception for each kind of failure (`ConflictException` so far), and module exceptions extend it with their `code`, so `common` never depends on a feature module. Errors that Spring raises itself get the name of their status as `code`. See [ARCHITECTURE.md](ARCHITECTURE.md#error-handling).
- **Alternatives:** an advice in every module (the format would be written several times); module exceptions that extend Spring's `ErrorResponseException` (ties them to the web layer).

## D12. Content is plain text

- **Decision:** the API stores and returns content exactly as written and never produces HTML. Clients escape it. See [API.md](API.md#conventions).
- **Why:** no HTML sanitizing on the server, and AI output is treated exactly like user input.
- **Cost:** any formatting (such as Markdown) is the client's job, including sanitizing what it renders.

## D13. Comment threads are flat lists; liked state comes from the `like` module

- **Decision:** `GET /posts/{id}/comments` returns a flat list with `parentId`. Whether the user liked something comes from `/me/likes/...` lookups, not from post and comment responses. See [API.md](API.md#likes).
- **Why:** flat lists stay shallow however deep a thread gets. The lookups keep `post` and `comment` independent of `like` (D1).
- **Alternatives:** nested JSON trees; a composition module that depends on `post`, `comment` and `like` to build combined responses.
- **Cost:** clients build the tree and make one extra request for liked state.

## D14. Deleting an account works the way Reddit does it

- **Decision:** deleting an account keeps the user row as a tombstone. The email and password hash are cleared, the username is retired for good, and the account's posts, comments and likes stay, with the author shown as "[deleted]". Deletion needs the password and cannot be undone. See [DOMAIN.md](DOMAIN.md#flows).
- **Why:** other people's threads and like counts stay intact, every foreign key stays valid with no cascades across modules, and the other modules only need to show a missing author. A retired username cannot be taken by someone else to pose as the old account.
- **Alternatives:** deleting everything the account created (tears holes in other people's threads, and every module must react to the deletion); soft-deleting all its posts and comments (hides content that others replied to).
- **Cost:** content outlives the account, so users must delete it first if they want it gone. That may not satisfy every legal erasure request (see [SECURITY.md](../SECURITY.md#known-limitations)).

## D15. IDs are UUIDv7, generated by Hibernate

- **Decision:** every entity ID is a UUID version 7, generated by Hibernate when the entity is first saved (`@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`). See [DATABASE.md](DATABASE.md#conventions).
- **Why:** unlike counting numbers, UUIDs are hard to guess and don't reveal how many rows exist, and the ID is known as soon as the entity is saved. Version 7 starts with a timestamp, so new rows are added at the end of the primary key index instead of at random places, which keeps inserts fast and the index compact.
- **Alternatives:** `bigint` identity columns (smaller, but guessable); random UUIDs (version 4: inserts land all over the index); PostgreSQL 18's `uuidv7()` as a column default (the ID would only be known after the insert).
- **Cost:** 16 bytes per ID instead of 8, and an ID reveals when it was created. That is not a secret here, because `createdAt` is public anyway.

## D16. `common` is a shared, open module

- **Decision:** shared infrastructure lives in one module, `common`, declared shared on the application class (`@Modulithic(sharedModules = "common")`) and open in its `package-info.java` (`type = OPEN`). See [ARCHITECTURE.md](ARCHITECTURE.md#modules).
- **Why:** every module needs it. As a shared module, every module may use it without listing it in `allowedDependencies`, and every module test starts it, which is how module tests get the `Clock` bean. As an open module, its sub-packages can be used too.
- **Alternatives:** listing `common` in every module's `allowedDependencies` and importing its configuration into every module test.
- **Cost:** everything in `common` is visible everywhere, so it must stay small: infrastructure only, with no business rules or entities.

## Open decisions

| Topic | Notes |
|---|---|
| LLM provider and model | Spring AI keeps it swappable; the app depends only on `LlmClient`. Compare cost per TL;DR and reply quality. |
| Hosting and deployment | Needs PostgreSQL with backups, a secret store and HTTPS (see [OPERATIONS.md](OPERATIONS.md#deployment)). |
| Code formatter | For example Spotless with a standard Java format, enforced in CI. |
| Comment thread pagination | Threads are returned whole. Needed once threads get large. |
| `ETag` / `If-Match` on edits | Would make edits safe between clients, not just on the server (see [API.md](API.md#concurrency)). |

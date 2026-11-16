# Testing

> **Status:** in use. Tests exist for the module boundaries, the application startup, the `user`, `post`, `auth`, `comment` and `like` modules and their endpoints, and the error advice (see [Current tests](#current-tests)). The rest of this strategy applies as each feature is built.

## Principles

- **Real PostgreSQL, never H2.** The design relies on PostgreSQL features: `ON CONFLICT`, `FOR UPDATE SKIP LOCKED`, partial indexes and check constraints. Tests run the real Flyway migrations against PostgreSQL in Testcontainers, so every build also tests the schema.
- **No real LLM** *(planned: `ai` step)*. A `FakeLlmClient` replaces `LlmClient` in tests. It can return a given text, fail with a retryable or a final error, or wait, and it records every prompt it receives. CI never needs an API key or network access to a provider.
- **Controlled time.** Code reads time from the injected `Clock` (see [ARCHITECTURE.md](ARCHITECTURE.md#conventions)). No test has needed to control it yet. The worker tests will use a clock they can move forward, to expire leases and reach backoff times without sleeping.
- **No sleeping or polling** *(planned: `ai` step)*. Scheduling is off in tests (`blog.ai.worker.enabled=false`), and tests run the worker by calling it directly, one poll at a time.
- **No test-managed transactions where commits matter.** Tests are not `@Transactional`: the behaviour under test depends on real commits, and later on the worker's separate transactions.

## Test levels

| Level | What it covers | Tools | Today |
|---|---|---|---|
| Domain unit tests | Entity rules, without Spring: `Post.edit()` and `tldrStatus()`, `Comment.reply()` and `softDelete()`, the `AiRequest` transitions and `isHeldBy()`, prompt construction and its size budget | JUnit 5 | none yet; the `User` and `Post` rules are tested through their module tests |
| Architecture | Module boundaries (`ApplicationModules.of(BlogApplication.class).verify()`); also writes the module diagrams | Spring Modulith | `ModularityTests` |
| Module tests | One module on its own, or with the modules it depends on (`mode = BootstrapMode.DIRECT_DEPENDENCIES`), always with the shared `common` module; events included | `@ApplicationModuleTest`, `Scenario`, Testcontainers | `UserModuleTests`, `PostModuleTests`, `AuthModuleTests` |
| Persistence | Migrations, constraints, foreign key actions, the SQL statements in [DATABASE.md](DATABASE.md) | Testcontainers, `JdbcTemplate` | inside the module tests |
| Concurrency | The races the design claims to handle (below) | Testcontainers, several threads released together | two registrations of one username (`UserModuleTests`); two likes or two unlikes by one user (`LikeModuleTests`) |
| API | Status codes, Problem Details bodies, validation, who may call what | `MockMvcTester`, Spring Security test | `AuthModuleTests` and the `*ApiTests` classes; `GlobalExceptionHandlerTests` for errors that are hard to cause through a real endpoint |
| End to end | Whole flows through HTTP, the database and the worker with the fake LLM | `@SpringBootTest`, Testcontainers | `BlogApplicationTests` only checks that the application starts |

## Current tests

| Class | What it checks |
|---|---|
| `ModularityTests` | The module boundaries, and writes the module diagrams |
| `BlogApplicationTests` | The whole application starts against PostgreSQL, and Hibernate accepts the schema that Flyway created |
| `common.error.GlobalExceptionHandlerTests` | Without Spring: an optimistic locking failure becomes `409 EDIT_CONFLICT`, and an unexpected error a `500` that hides its details |
| `user.UserModuleTests` | Emails stored in lower case, the bot user, taken emails and usernames refused ignoring case (including the bot's username and a deleted account's), a deleted account's email free again, only one of two simultaneous registrations getting a username, deleted accounts hidden, deleting an account (twice, and never the bot), credentials found by email ignoring case, and three database constraints (usernames unique ignoring case; a regular user needs a password; the bot can't be deleted) |
| `user.UserApiTests` | `GET /users/{id}`: a profile without the email, the bot, an unknown user (`404`) |
| `post.PostModuleTests` | Creating, finding, editing, deleting and listing posts; unknown and deleted authors; blank titles; only the author may edit; optimistic locking; saving a post loaded before a like keeps the like count; the database rejects an unknown author |
| `post.PostApiTests` | The `/posts` endpoints: creating with a `Location` header, reading, validation, `401` without a token, `404`, `400` for an ID that is not a UUID, editing and deleting by the author, `403 NOT_AUTHOR` for anyone else, pages newest first with their metadata, the page size limit, and a deleted author shown as `null` |
| `comment.CommentModuleTests` | Threads oldest first, a missing post, a parent on another post or missing, soft delete keeping the replies (twice), no edits or replies on a deleted comment, only the author may edit or delete, the bot's replies, deleting a post deleting its comments, saving a comment loaded before a like keeps the like count, and two database constraints (a reply on another post than its parent; a deleted comment with content) |
| `comment.CommentApiTests` | The comment endpoints: writing a comment and a reply with a `Location` header, the thread, `401` without a token, validation, `400` for a parent on another post, `409 TARGET_DELETED`, `404` for a missing post, editing and deleting by the author (the deleted comment shown without content or author), `403` for anyone else and for the bot's comments, and a deleted account's comment keeping its content |
| `like.LikeModuleTests` | Liking and unliking are idempotent, counts from several users, two parallel likes and two parallel unlikes by one user, a deleted comment can't be liked but can be unliked, missing targets, which targets a user likes, likes staying when the account is deleted, and deleting a post deleting its likes and its comments' likes |
| `like.LikeApiTests` | The like endpoints: liking and unliking with the count in the post or comment, `401` without a token, `404` for a missing post, `409 TARGET_DELETED` for a deleted comment, the liked-state lookup, and its 1–100 IDs limit |
| `auth.AuthModuleTests` | Through MockMvc: registration (success, the password stored as a bcrypt hash, invalid and missing fields, a password over 72 bytes, taken email or username, a body that is not JSON); login (email in any case, the same answer for an unknown email and a wrong password); tokens (the token identifies the user; `401 UNAUTHENTICATED` without a token, with an expired one, one signed with another key, a broken one even on a public endpoint, and one of a deleted account); `GET /me`; deleting the account (a wrong or missing password, and everything that changes afterwards) |
| `auth.JwtSecretTests` | Without Spring: a missing, short or non-Base64 secret, and the dev secret outside the `dev` profile, stop the application from starting |

## Required tests

Each of these checks a claim made in the design documents. A feature is not done until its tests from this list exist and pass.

**Registration** *(all exist)*
- A registration stores the email in lower case, and the password only as a bcrypt hash.
- Invalid input returns `400 VALIDATION_FAILED` with an entry for each broken field. A password over 72 bytes is rejected.
- A taken email or username returns `409`, ignoring case. The bot's username is taken.
- Two registrations of the same username at the same moment: one succeeds, the other gets `UsernameTakenException`.

**Likes** *(all exist)*
- Two parallel likes by the same user create one row, and `like_count` is 1. The same for unlikes.
- Liking a deleted comment returns `409`; liking a missing post returns `404`.

**Lost updates** *(exist, except the TL;DR columns)*
- Saving a post that was loaded before a like does not overwrite `like_count`. The same for the TL;DR columns and for comments.
- Two simultaneous edits of the same post: one succeeds, the other gets `409 EDIT_CONFLICT`.

**TL;DR**
- Creating a post queues a TL;DR job in the same transaction; if the post is rolled back, so is the job.
- A post edited while its TL;DR is being generated keeps `PENDING`: the stale result is discarded, and the newer job's result is applied.
- A superseded job is cancelled without calling the LLM (the fake records no call).
- A title-only edit queues no job.
- After the last attempt fails, `tldrStatus` is `FAILED`; a later content edit makes it `PENDING` again.

**Worker**
- Two workers polling at the same time never claim the same job.
- When a lease expires, another worker claims the job again, and the first worker's late result is discarded (`isHeldBy` fails).
- A retryable error moves the job back to `PENDING` with the expected backoff; after the last attempt it is `FAILED`.
- A job whose lease expired with no attempts left becomes `FAILED`.
- The app refuses to start when the lease is not longer than the LLM timeout.

**AI replies**
- A reply to a comment that is deleted before the worker runs is `CANCELLED`, and no bot comment is created.
- The prompt contains the ancestors, root first, and the target; when it is over budget, the oldest ancestors are dropped first.
- A completed request has exactly one bot comment, even when two workers raced on it.

**Account deletion** *(all exist, except the part about AI replies)*
- Deleting needs the correct password: a wrong one returns `403` and changes nothing.
- After deletion, the account's token gets `401` on every endpoint, and logging in with the old email fails.
- The account's posts and comments stay, with `author: null`, and like counts don't change.
- `GET /users/{id}` returns `404`, and `GET /posts?authorId=` returns an empty page.
- The username cannot be registered again; the email can.
- An AI reply requested before the deletion still completes.

**Data integrity** *(exist, except the part about AI requests)*
- The database rejects a reply whose `post_id` differs from its parent's.
- The database rejects an active user without an email or password hash, a deleted account that still has one, a deleted bot, and a comment that is deleted but still has content.
- Deleting a post deletes its comments and likes, and sets the references in its AI requests to null.

**Security** *(exist for the endpoints built so far)*
- Every protected endpoint returns `401` without a token, with an expired token, and with the token of a deleted account.
- Editing or deleting someone else's post or comment returns `403`.
- The bot user cannot log in.
- Unknown email and wrong password return the same `401` response.
- `GET /ai-requests/{id}` returns `404` to anyone but the requester.

## Conventions

- **Naming:** test classes end in `Tests` (`PostModuleTests`), and a module's tests sit in the module's package, so they can use its package-private types. Test methods state the behaviour they check (`rejectsBlankTitle`, `listsAnAuthorsPostsNewestFirst`).
- **Profile:** every Spring test class has `@ActiveProfiles("test")`, which loads `src/test/resources/application-test.properties` (the test JWT secret, and later the fake LLM).
- **Containers:** `TestcontainersConfiguration` defines the PostgreSQL container as a bean, and `@ServiceConnection` connects Spring to it. Each Spring test setup gets its own container, and test classes with the same setup share it, because Spring reuses their application context. That is why `PostApiTests` has exactly the same annotations as `PostModuleTests`. A full run starts six today: one each for `BlogApplicationTests`, the `user`, `post`, `comment` and `like` tests, and `AuthModuleTests`.
- **Test data:** tables are not emptied between tests, so every test creates its own data with values no other test uses, such as random usernames (`newUser()` in `PostModuleTests`). Tests create only the data they need, through small helper methods. Builders can come later, when the data gets bigger.
- **API tests:** put them in `<Name>ApiTests`, with the module test's annotations plus `extraIncludes = "auth"` (the security configuration lives there) and `@AutoConfigureMockMvc`. Send requests with `MockMvcTester`; they pass through the security filters and the controller without a real server. Log a request in with `.with(jwt().jwt(j -> j.subject(userId.toString())))` from Spring Security's test support, which skips real tokens; `AuthModuleTests` covers those. Assert on the status, the `code` and the field names. Don't assert on the validation library's own messages ("must not be blank"): they follow the language of the machine that runs the tests.
- **Coverage:** no percentage target. The measure is the list above: every rule in [DOMAIN.md](DOMAIN.md) has a test.

## Running the tests

Docker must be running.

```bash
./mvnw verify
```

This runs every test, as CI does. To run one class, use `./mvnw test -Dtest=PostModuleTests`. `./mvnw test` also runs everything, because all tests run in the `test` phase today. If slow end-to-end tests are added later, they can be named `*IT` and run with Failsafe in the `verify` phase only.

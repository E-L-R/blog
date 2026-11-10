# Testing

> **Status:** in use. Tests exist for the module boundaries, the application startup, the `user` and `post` modules, and the registration endpoint (see [Current tests](#current-tests)). The rest of this strategy applies as each feature is built.

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
| Concurrency | The races the design claims to handle (below) | Testcontainers, several threads released together | two registrations of one username, in `UserModuleTests` |
| API | Status codes, Problem Details bodies, validation, who may call what | `MockMvcTester`, Spring Security test | `AuthModuleTests`, for registration |
| End to end | Whole flows through HTTP, the database and the worker with the fake LLM | `@SpringBootTest`, Testcontainers | `BlogApplicationTests` only checks that the application starts |

## Current tests

| Class | What it checks |
|---|---|
| `ModularityTests` | The module boundaries, and writes the module diagrams |
| `BlogApplicationTests` | The whole application starts against PostgreSQL, and Hibernate accepts the schema that Flyway created |
| `user.UserModuleTests` | Emails stored in lower case, the bot user, taken emails and usernames refused ignoring case (including the bot's username and a deleted account's), a deleted account's email free again, only one of two simultaneous registrations getting a username, deleted accounts hidden, and three database constraints (usernames unique ignoring case; a regular user needs a password; the bot can't be deleted) |
| `post.PostModuleTests` | Creating, finding, editing, deleting and listing posts; unknown and deleted authors; blank titles; only the author may edit; optimistic locking; the database rejects an unknown author |
| `auth.AuthModuleTests` | `POST /auth/register` through MockMvc: a successful registration, the password stored as a bcrypt hash, invalid and missing fields, a password over 72 bytes, a taken email or username (`409`), a body that is not JSON, and `401` for every other request |

## Required tests

Each of these checks a claim made in the design documents. A feature is not done until its tests from this list exist and pass.

**Registration** *(all exist)*
- A registration stores the email in lower case, and the password only as a bcrypt hash.
- Invalid input returns `400 VALIDATION_FAILED` with an entry for each broken field. A password over 72 bytes is rejected.
- A taken email or username returns `409`, ignoring case. The bot's username is taken.
- Two registrations of the same username at the same moment: one succeeds, the other gets `UsernameTakenException`.

**Likes**
- Two parallel likes by the same user create one row, and `like_count` is 1. The same for unlikes.
- Liking a deleted comment returns `409`; liking a missing post returns `404`.

**Lost updates**
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

**Account deletion**
- Deleting needs the correct password: a wrong one returns `403` and changes nothing.
- After deletion, the account's token gets `401` on every endpoint, and logging in with the old email fails.
- The account's posts and comments stay, with `author: null`, and like counts don't change.
- `GET /users/{id}` returns `404`, and `GET /posts?authorId=` returns an empty page.
- The username cannot be registered again; the email can.
- An AI reply requested before the deletion still completes.

**Data integrity**
- The database rejects a reply whose `post_id` differs from its parent's.
- The database rejects an active user without an email or password hash, a deleted account that still has one, a deleted bot, and a comment that is deleted but still has content.
- Deleting a post deletes its comments and likes, and sets the references in its AI requests to null.

**Security**
- Every protected endpoint returns `401` without a token, with an expired token, and with the token of a deleted account.
- Editing or deleting someone else's post or comment returns `403`.
- The bot user cannot log in.
- Unknown email and wrong password return the same `401` response.
- `GET /ai-requests/{id}` returns `404` to anyone but the requester.

## Conventions

- **Naming:** test classes end in `Tests` (`PostModuleTests`), and a module's tests sit in the module's package, so they can use its package-private types. Test methods state the behaviour they check (`rejectsBlankTitle`, `listsAnAuthorsPostsNewestFirst`).
- **Containers:** `TestcontainersConfiguration` defines the PostgreSQL container as a bean, and `@ServiceConnection` connects Spring to it. Each Spring test setup gets its own container, and test classes with the same setup share it, because Spring reuses their application context. A full run starts four today: one each for `BlogApplicationTests`, `UserModuleTests`, `PostModuleTests` and `AuthModuleTests`.
- **Test data:** tables are not emptied between tests, so every test creates its own data with values no other test uses, such as random usernames (`newUser()` in `PostModuleTests`). Tests create only the data they need, through small helper methods. Builders can come later, when the data gets bigger.
- **API tests:** add `@AutoConfigureMockMvc` to the module test and send requests with `MockMvcTester`. They pass through the security filters and the controller without a real server. Assert on the status, the `code` and the field names. Don't assert on the validation library's own messages ("must not be blank"): they follow the language of the machine that runs the tests.
- **Coverage:** no percentage target. The measure is the list above: every rule in [DOMAIN.md](DOMAIN.md) has a test.

## Running the tests

Docker must be running.

```bash
./mvnw verify
```

This runs every test, as CI does. To run one class, use `./mvnw test -Dtest=PostModuleTests`. `./mvnw test` also runs everything, because all tests run in the `test` phase today. If slow end-to-end tests are added later, they can be named `*IT` and run with Failsafe in the `verify` phase only.

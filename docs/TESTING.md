# Testing

> **Status:** in use. Tests exist for the module boundaries, the application startup, the `user` and `post` modules, and the registration endpoint (see [Current tests](#current-tests)), and for the frontend's pages and flows (see [Frontend tests](#frontend-tests)). The rest of this strategy applies as each feature is built.

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

## Frontend tests

The frontend in `frontend/` has its own tests, run with npm, not Maven. They need neither Docker nor the backend: requests go to the mock API ([FRONTEND.md](FRONTEND.md#mock-api)), which implements [API.md](API.md).

### Principles

- **Test through the user's eyes.** Page tests render the real app, with its real routes and providers, at a URL, and find things the way a user does: by role, label and text (Testing Library). They never look at component state or CSS classes, so a refactoring that keeps the behaviour keeps the tests green.
- **Mock the network, nothing else.** MSW intercepts `fetch`, so the app's HTTP client, its error handling and its cache all run for real. Tests never stub `apiRequest` or a query hook.
- **A request with no handler fails the test** (`onUnhandledFrame: 'error'`).
- **A clean slate for every test:** a fresh mock database (with only the bot user), no token, an empty DOM (`src/test/setup.ts`).
- **No sleeping.** Tests wait for what should appear (`findBy...`, Playwright's auto-waiting), never for a fixed time. The mock writes the TL;DR at once in tests, unless a test sets a delay itself.
- **Controlled time.** Code that formats time takes the current time as an argument, and the token store takes a clock, so tests pass their own.

### Test levels

| Level | What it covers | Tools | Where |
|---|---|---|---|
| Boundaries | The feature boundaries, and no raw HTML | ESLint (`npm run lint`) | `eslint.config.js` |
| Types | The whole app, the tests and the configuration | TypeScript (`npm run typecheck`) | |
| Unit | Logic without React: validation rules, error mapping, time formatting, the token store, the HTTP client, the redirect check | Vitest | `*.test.ts` next to the code |
| Component | One component on its own, for states that are hard to reach through a page | Vitest, Testing Library | `Tldr.test.tsx` |
| Page | A page in the whole app, against the mock API: what it shows, what it sends, and where it navigates | Vitest, Testing Library, user-event, MSW | `*Page.test.tsx`, `session.test.tsx`, `routes.test.tsx` |
| End to end | The main flows in a real browser, against the app in mock mode | Playwright (Chromium) | `frontend/e2e/` |

### Current frontend tests

| File | What it checks |
|---|---|
| `api/token.test.ts` | The token is kept until it expires, survives a reload, can be cleared; corrupt storage counts as no token; subscribers hear about changes, also from other tabs |
| `api/client.test.ts` | The token, body and query parameters are sent; `204` gives `undefined`; Problem Details become an `ApiError` with `code` and field errors; an empty `401` is `UNAUTHENTICATED`; other error bodies are `UNKNOWN`; a refused token is forgotten and a `GET` is sent again without it, but a write is not; no response is `NETWORK_ERROR` |
| `lib/validation.test.ts` | Every rule in [API.md](API.md#validation), including the password limit in UTF-8 bytes |
| `lib/errors.test.ts` | Messages chosen by `code`; the first server error of each field; which field a conflict is about |
| `lib/time.test.ts` | "just now", minutes, hours, "yesterday", days, then the date |
| `features/auth/redirect.test.ts` | Paths in the app are kept; other sites, `//host` and `javascript:` go home instead |
| `features/auth/RegisterPage.test.tsx` | A registration leads to the login page with the email (in lower case); browser validation sends nothing; a taken email (ignoring case) and the bot's username show at their fields; the server's field errors show at their fields; a logged-in user is sent home |
| `features/auth/LoginPage.test.tsx` | Logging in shows the user in the header; the user returns to the page that asked; never to another site; the same message for a wrong password and an unknown email; both fields required; a deleted account can't log in; a logged-in user is sent on |
| `features/auth/session.test.tsx` | Protected pages send anonymous users to log in, and back afterwards; logging out; a deleted account's token is dropped and the user is logged out |
| `features/posts/PostListPage.test.tsx` | Newest first, with the TL;DR and author; "[deleted]" for a deleted author; the empty state; paging, with the page in the URL |
| `features/posts/PostPage.test.tsx` | The post, its author, its TL;DR and the tab title; HTML in a title, content or TL;DR is shown as text; Edit and Delete for the author only; deleting after confirming, and cancelling; a missing post; the TL;DR appears once written |
| `features/posts/PostEditorPage.test.tsx` | Publishing opens the new post; title and content required; the character counter; editing saves; `EDIT_CONFLICT` and `NOT_AUTHOR` are explained; someone else's post can't be edited |
| `features/posts/Tldr.test.tsx` | Each TL;DR state: none, new and pending, pending with the old summary, generated, failed |
| `features/account/AccountPage.test.tsx` | The account and only its own posts; a login is needed; a wrong password deletes nothing; deleting logs out and leaves the posts by "[deleted]" |
| `features/account/UserPage.test.tsx` | A profile with its posts; the bot is marked; a deleted account is not found |
| `app/routes.test.tsx` | The not-found page; the skip link |
| `e2e/auth.spec.ts` | Sign up, log in, stay logged in after a reload, log out; a protected page and back |
| `e2e/posts.spec.ts` | Write a post, see its TL;DR appear, edit it, delete it; visitors get no edit controls |
| `e2e/account.spec.ts` | Delete an account: its post stays by "[deleted]", and its username can't be registered again |

### Required frontend tests

Each of these checks a promise the frontend makes. A frontend feature is not done until its tests from this list exist and pass.

**Accounts** *(all exist)*
- Registration leads to the login page; taken emails and usernames show at their fields.
- Login returns to the page that asked for it, and never to another site.
- A token the API refuses logs the user out, and public pages still load.
- Deleting the account needs the password, logs the user out, and their posts show "[deleted]".

**Posts** *(all exist)*
- Content, titles and TL;DRs are shown as text, never as HTML.
- Only the author sees Edit and Delete.
- Each `tldrStatus` is shown, and a pending TL;DR appears without a reload.
- `EDIT_CONFLICT` tells the user to reload and try again.

**Comments** *(planned)*
- The flat list from the API is shown as a tree, ordered oldest first at each level.
- A deleted comment shows "[deleted]" and keeps its replies; it has no Reply, Edit or Like.
- Replying to a comment deleted in the meantime explains `TARGET_DELETED`.

**Likes** *(planned)*
- Liked state comes from `/me/likes/...` for the items on screen, in one request per list.
- A like shows at once, and is undone if the request fails.
- Anonymous users are asked to log in instead.

**AI replies** *(planned)*
- Asking for a reply shows its progress, polls `GET /ai-requests/{id}` and stops at a final status.
- A completed request shows the bot's comment in the thread; `FAILED` and `CANCELLED` show a generic message.

### Frontend conventions

- **Naming:** test files sit next to the code they test, as `<File>.test.ts(x)`; end-to-end tests are `e2e/<flow>.spec.ts`. Test names state the behaviour (`'shows content as plain text, never as HTML'`).
- **Test data:** each test creates what it needs with the helpers in `src/test/render.tsx` (`createUser`, `createLoggedInUser`) and the mock database (`db.createPost`). Usernames are unique per test. End-to-end tests start from the demo data in a fresh browser context.
- **Errors the mock doesn't produce**, such as `EDIT_CONFLICT`, are set up in the test with `server.use(...)`.
- **Queries:** prefer `getByRole` with a name, then `getByLabelText`, then `getByText`. Check accessible descriptions for field errors (`toHaveAccessibleDescription`), which also proves that screen readers get them.
- **Server wording:** assert on the app's own messages, never on the server's `detail` or validation texts.

### Running the frontend tests

From `frontend/`:

```bash
npm test          # unit, component and page tests (Vitest)
npm run e2e       # end-to-end tests (Playwright); starts the app in mock mode itself
npm run check     # lint, formatting, types, the Vitest tests and the build, as CI runs them
```

`npm run test:watch` reruns the affected tests on every save. Before the first `npm run e2e`, install the browser once with `npx playwright install chromium`.

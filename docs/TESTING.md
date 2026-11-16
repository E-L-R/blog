# Testing

> **Status:** in use. Tests exist for the module boundaries, the application startup, every module and its endpoints, and the error advice (see [Current tests](#current-tests)), and for the frontend's pages and flows (see [Frontend tests](#frontend-tests)).

## Principles

- **Real PostgreSQL, never H2.** The design relies on PostgreSQL features: `ON CONFLICT`, `FOR UPDATE SKIP LOCKED`, partial indexes and check constraints. Tests run the real Flyway migrations against PostgreSQL in Testcontainers, so every build also tests the schema.
- **No real LLM.** A `FakeLlmClient` replaces `LlmClient` in tests (`blog.ai.fake=true`). It can return a given text, fail with a retryable or a final error, or wait, and it records every prompt it receives. CI never needs an API key or network access to a provider.
- **Controlled time.** Code reads time from the injected `Clock` (see [ARCHITECTURE.md](ARCHITECTURE.md#conventions)). The AI tests replace it with `MutableClock`, which they move forward to expire leases and reach backoff times without sleeping.
- **No sleeping or polling.** Scheduling is off in tests (`blog.ai.worker.enabled=false`), and tests run the worker by calling it directly, one poll at a time.
- **No test-managed transactions where commits matter.** Tests are not `@Transactional`: the behaviour under test depends on real commits, and on the worker's separate transactions.

## Test levels

| Level | What it covers | Tools | Today |
|---|---|---|---|
| Domain unit tests | Rules that need no database, without Spring | JUnit 5 | prompt construction and its size budget (`PromptBuilderTests`), the AI settings (`AiPropertiesTests`), the Ollama client against a stand-in server (`OllamaLlmClientTests`), the JWT secret (`JwtSecretTests`); the entity rules are tested through their module tests |
| Architecture | Module boundaries (`ApplicationModules.of(BlogApplication.class).verify()`); also writes the module diagrams | Spring Modulith | `ModularityTests` |
| Module tests | One module on its own, or with the modules it depends on (`mode = BootstrapMode.DIRECT_DEPENDENCIES` or `ALL_DEPENDENCIES`), always with the shared `common` module; events included | `@ApplicationModuleTest`, Testcontainers | one `<Name>ModuleTests` per module |
| Persistence | Migrations, constraints, foreign key actions, the SQL statements in [DATABASE.md](DATABASE.md) | Testcontainers, `JdbcTemplate` | inside the module tests |
| Concurrency | The races the design claims to handle (below) | Testcontainers, several threads released together | two registrations of one username (`UserModuleTests`); two likes or two unlikes by one user (`LikeModuleTests`); two workers claiming, and two workers finishing the same reply (`AiModuleTests`) |
| API | Status codes, Problem Details bodies, validation, who may call what | `MockMvcTester`, Spring Security test | `AuthModuleTests` and the `*ApiTests` classes; `GlobalExceptionHandlerTests` for errors that are hard to cause through a real endpoint |
| End to end | Whole flows through HTTP, the database and the worker with the fake LLM | `@SpringBootTest`, Testcontainers | `AiApiTests` asks for a reply through HTTP, runs the worker and reads the bot's comment; `BlogApplicationTests` checks that the whole application starts |

## Current tests

| Class | What it checks |
|---|---|
| `ModularityTests` | The module boundaries, and writes the module diagrams |
| `BlogApplicationTests` | The whole application starts against PostgreSQL, and Hibernate accepts the schema that Flyway created. It shares its context with `ApiDocumentationTests`. |
| `ApiDocumentationTests` | The OpenAPI description lists every endpoint, marks only those that need a token, hides the current-user parameter, and has the summaries; Swagger UI and the readiness check answer |
| `common.error.GlobalExceptionHandlerTests` | Without Spring: an optimistic locking failure becomes `409 EDIT_CONFLICT`, and an unexpected error a `500` that hides its details |
| `user.UserModuleTests` | Emails stored in lower case, the bot user, taken emails and usernames refused ignoring case (including the bot's username and a deleted account's), a deleted account's email free again, only one of two simultaneous registrations getting a username, deleted accounts hidden, deleting an account (twice, and never the bot), credentials found by email ignoring case, and three database constraints (usernames unique ignoring case; a regular user needs a password; the bot can't be deleted) |
| `user.UserApiTests` | `GET /users/{id}`: a profile without the email, the bot, an unknown user (`404`) |
| `post.PostModuleTests` | Creating, finding, editing, deleting and listing posts; unknown and deleted authors; blank titles; only the author may edit; optimistic locking; saving a post loaded before a like keeps the like count; the database rejects an unknown author |
| `post.PostApiTests` | The `/posts` endpoints: creating with a `Location` header, reading, validation, `401` without a token, `404`, `400` for an ID that is not a UUID, editing and deleting by the author, `403 NOT_AUTHOR` for anyone else, pages newest first with their metadata, the page size limit, and a deleted author shown as `null` |
| `comment.CommentModuleTests` | Threads oldest first, a missing post, a parent on another post or missing, soft delete keeping the replies (twice), no edits or replies on a deleted comment, only the author may edit or delete, the bot's replies, deleting a post deleting its comments, saving a comment loaded before a like keeps the like count, and two database constraints (a reply on another post than its parent; a deleted comment with content) |
| `comment.CommentApiTests` | The comment endpoints: writing a comment and a reply with a `Location` header, the thread, `401` without a token, validation, `400` for a parent on another post, `409 TARGET_DELETED`, `404` for a missing post, editing and deleting by the author (the deleted comment shown without content or author), `403` for anyone else and for the bot's comments, and a deleted account's comment keeping its content |
| `like.LikeModuleTests` | Liking and unliking are idempotent, counts from several users, two parallel likes and two parallel unlikes by one user, a deleted comment can't be liked but can be unliked, missing targets, which targets a user likes, likes staying when the account is deleted, and deleting a post deleting its likes and its comments' likes |
| `like.LikeApiTests` | The like endpoints: liking and unliking with the count in the post or comment, `401` without a token, `404` for a missing post, `409 TARGET_DELETED` for a deleted comment, the liked-state lookup, and its 1–100 IDs limit |
| `ai.AiModuleTests` | With the fake LLM and a clock moved by hand: creating a post queues a TL;DR that the worker writes; a rolled-back post leaves no job; a title-only edit queues none; only the newest of several jobs calls the model; an edit during generation discards the stale result; backoff after retryable errors until the last attempt fails, and a later edit makes the status `PENDING` again; a final error fails at once; a call that takes too long is retried; two workers never claim the same job; an expired lease is claimed again and the late result discarded; a lease that expired with no attempts left fails; the finished-jobs metric; replies with the thread in the prompt, to a comment or to the post; a reply to a comment deleted before the worker runs is cancelled; two workers racing on a reply create one bot comment; a reply asked before the account was deleted still completes; deleting a post detaches its requests, which the worker cancels |
| `ai.AiApiTests` | The AI endpoints: asking for a reply (`202` with a `Location`) and following it to the bot's comment, only the requester sees a request, `401` without a token, `404` for a missing target and `409` for a deleted comment; and `tldr` and `tldrStatus` in post responses |
| `ai.PromptBuilderTests` | Without Spring: the TL;DR and reply prompts, the thread root first with deleted comments as "[deleted]", and the size budget (oldest comments dropped first, then the post shortened, the target kept) |
| `ai.AiPropertiesTests` | Without Spring: a lease not longer than the LLM timeout stops the application from starting; so does Ollama with a Claude model; the backoff doubles |
| `ai.OllamaLlmClientTests` | Without Spring, against a stand-in Ollama server from the JDK: the request (model, system and user messages, no streaming, the token limit); the answer and its model; reasoning in `<think>` tags left out; a cut-off or empty answer fails for good, and so does a model that was never pulled (`404`); a `5xx`, a server that answers too late and an unreachable server are retried, with a message that says which |
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

**Lost updates** *(exist; the TL;DR columns are covered by `anEditDuringGenerationDiscardsTheStaleResult`)*
- Saving a post that was loaded before a like does not overwrite `like_count`. The same for the TL;DR columns and for comments.
- Two simultaneous edits of the same post: one succeeds, the other gets `409 EDIT_CONFLICT`.

**TL;DR** *(all exist)*
- Creating a post queues a TL;DR job in the same transaction; if the post is rolled back, so is the job.
- A post edited while its TL;DR is being generated keeps `PENDING`: the stale result is discarded, and the newer job's result is applied.
- A superseded job is cancelled without calling the LLM (the fake records no call).
- A title-only edit queues no job.
- After the last attempt fails, `tldrStatus` is `FAILED`; a later content edit makes it `PENDING` again.

**Worker** *(all exist)*
- Two workers polling at the same time never claim the same job.
- When a lease expires, another worker claims the job again, and the first worker's late result is discarded (`isHeldBy` fails).
- A retryable error moves the job back to `PENDING` with the expected backoff; after the last attempt it is `FAILED`.
- A job whose lease expired with no attempts left becomes `FAILED`.
- The app refuses to start when the lease is not longer than the LLM timeout.

**AI replies** *(all exist)*
- A reply to a comment that is deleted before the worker runs is `CANCELLED`, and no bot comment is created.
- The prompt contains the ancestors, root first, and the target; when it is over budget, the oldest ancestors are dropped first.
- A completed request has exactly one bot comment, even when two workers raced on it.

**Account deletion** *(all exist)*
- Deleting needs the correct password: a wrong one returns `403` and changes nothing.
- After deletion, the account's token gets `401` on every endpoint, and logging in with the old email fails.
- The account's posts and comments stay, with `author: null`, and like counts don't change.
- `GET /users/{id}` returns `404`, and `GET /posts?authorId=` returns an empty page.
- The username cannot be registered again; the email can.
- An AI reply requested before the deletion still completes.

**Data integrity** *(all exist)*
- The database rejects a reply whose `post_id` differs from its parent's.
- The database rejects an active user without an email or password hash, a deleted account that still has one, a deleted bot, and a comment that is deleted but still has content.
- Deleting a post deletes its comments and likes, and sets the references in its AI requests to null.

**Security** *(all exist)*
- Every protected endpoint returns `401` without a token, with an expired token, and with the token of a deleted account.
- Editing or deleting someone else's post or comment returns `403`.
- The bot user cannot log in.
- Unknown email and wrong password return the same `401` response.
- `GET /ai-requests/{id}` returns `404` to anyone but the requester.

## Conventions

- **Naming:** test classes end in `Tests` (`PostModuleTests`), and a module's tests sit in the module's package, so they can use its package-private types. Test methods state the behaviour they check (`rejectsBlankTitle`, `listsAnAuthorsPostsNewestFirst`).
- **Profile:** every Spring test class has `@ActiveProfiles("test")`, which loads `src/test/resources/application-test.properties` (the test JWT secret, the fake LLM, and the worker's schedule turned off).
- **Containers:** `TestcontainersConfiguration` defines the PostgreSQL container as a bean, and `@ServiceConnection` connects Spring to it. Each Spring test setup gets its own container, and test classes with the same setup share it, because Spring reuses their application context. That is why `PostApiTests` has exactly the same annotations as `PostModuleTests`. A full run starts seven: one each for `BlogApplicationTests`, the `user`, `post`, `comment`, `like` and `ai` tests, and `AuthModuleTests`.
- **Test data:** tables are not emptied between tests, so every test creates its own data with values no other test uses, such as random usernames (`newUser()` in `PostModuleTests`). The AI job queue is the exception: the worker takes any due job, so the AI tests cancel the unfinished jobs before each test (`AiTestConfiguration.Reset`). Tests create only the data they need, through small helper methods. Builders can come later, when the data gets bigger.
- **API tests:** put them in `<Name>ApiTests`, with the module test's annotations plus `extraIncludes = "auth"` (the security configuration lives there) and `@AutoConfigureMockMvc`. Send requests with `MockMvcTester`; they pass through the security filters and the controller without a real server. Log a request in with `.with(jwt().jwt(j -> j.subject(userId.toString())))` from Spring Security's test support, which skips real tokens; `AuthModuleTests` covers those. Assert on the status, the `code` and the field names. Don't assert on the validation library's own messages ("must not be blank"): they follow the language of the machine that runs the tests.
- **Coverage:** no percentage target. The measure is the list above: every rule in [DOMAIN.md](DOMAIN.md) has a test.

## Running the tests

Docker must be running.

```bash
./mvnw verify
```

This runs every test, as CI does. To run one class, use `./mvnw test -Dtest=PostModuleTests`. `./mvnw test` also runs everything, because all tests run in the `test` phase today. If slow end-to-end tests are added later, they can be named `*IT` and run with Failsafe in the `verify` phase only.

## Frontend tests

The frontend in `frontend/` has its own tests, run with npm, not Maven. Requests go to the mock API ([FRONTEND.md](FRONTEND.md#mock-api)), which implements [API.md](API.md); the end-to-end tests also run against the real backend.

### Principles

- **Mock the API in unit and page tests; use both in end-to-end tests.** Unit and page tests need neither Docker nor the backend. The end-to-end tests also run against the real backend, in CI too, to catch differences between it and the mock.
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
| End to end | The main flows in a real browser, against the app in mock mode, and the same tests against the real backend | Playwright (Chromium) | `frontend/e2e/` |

### Current frontend tests

| File | What it checks |
|---|---|
| `api/token.test.ts` | The token is kept until it expires, survives a reload, can be cleared; corrupt storage counts as no token; subscribers hear about changes, also from other tabs |
| `api/client.test.ts` | The token, body and query parameters are sent; `204` gives `undefined`; Problem Details become an `ApiError` with `code` and field errors; a `401` without a body is `UNAUTHENTICATED`; other error bodies are `UNKNOWN`; a refused token is forgotten and a `GET` is sent again without it, but a write is not; no response is `NETWORK_ERROR` |
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
| `features/comments/thread.test.ts` | The tree from a flat list: replies under their parent, the order kept at every level, a reply whose parent is missing kept at the top, deleted comments kept with their replies; the count leaves deleted comments out |
| `features/comments/CommentSection.test.tsx` | The thread as a tree, oldest first; visitors get a login link instead of the comment box, and Reply sends them to log in; adding a comment; text required; replying, with the reply under its parent; editing your own comment, and no Edit or Delete on others'; deleting keeps the replies under "[deleted]", with no actions left; `TARGET_DELETED` when the parent was deleted meanwhile; a deleted account's comment keeps its text by "[deleted]"; HTML in a comment shown as text |
| `features/likes/LikeButton.test.tsx` | A like shows before the API answers, and is saved; unliking; a failed like goes back and shows an error; one lookup for all the comments on screen; visitors are sent to log in and nothing is looked up; the like count on the post list |
| `features/ai/waitForReply.test.ts` | Asking until the request is final, waiting between polls; stopping at `FAILED`; giving up after a while with the request still pending |
| `features/ai/AskAiButton.test.tsx` | The bot's reply to a post appears in the thread, marked AI; a reply to a comment appears under it; "writing a reply…" until it is there; a message when the AI couldn't reply; no Ask AI on a deleted comment; visitors are sent to log in |
| `features/account/UserPage.test.tsx` | A profile with its posts; the bot is marked; a deleted account is not found |
| `app/routes.test.tsx` | The not-found page; the skip link |
| `e2e/auth.spec.ts` | Sign up, log in, stay logged in after a reload, log out; a wrong password; a protected page and back |
| `e2e/posts.spec.ts` | Write a post, see its TL;DR appear, edit it, delete it; visitors read a post but get no edit controls |
| `e2e/comments.spec.ts` | Comment and reply; like a post and a comment, still liked after a reload; ask the AI and see the bot's reply; edit, then delete, with the reply staying under "[deleted]"; visitors read comments but are sent to log in to take part |
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

**Comments** *(all exist)*
- The flat list from the API is shown as a tree, ordered oldest first at each level.
- A deleted comment shows "[deleted]" and keeps its replies; it has no Reply, Edit, Like or Ask AI.
- Only the author sees Edit and Delete.
- Replying to a comment deleted in the meantime explains `TARGET_DELETED`.
- Comment text is shown as text, never as HTML.

**Likes** *(all exist)*
- Liked state comes from `/me/likes/...` for the items on screen, in one request per list.
- A like shows at once, and is undone if the request fails.
- Anonymous users are asked to log in instead, and nothing is looked up for them.

**AI replies** *(all exist)*
- Asking for a reply shows its progress, polls `GET /ai-requests/{id}` and stops at a final status, or gives up after a while.
- A completed request shows the bot's comment in the thread, marked as AI; `FAILED` and `CANCELLED` show a generic message.

### Frontend conventions

- **Naming:** test files sit next to the code they test, as `<File>.test.ts(x)`; end-to-end tests are `e2e/<flow>.spec.ts`. Test names state the behaviour (`'shows content as plain text, never as HTML'`).
- **Test data:** each test creates what it needs with the helpers in `src/test/render.tsx` (`createUser`, `createLoggedInUser`) and the mock database (`db.createPost`). Usernames are unique per test. End-to-end tests create their own users and posts through the app (`e2e/helpers.ts`), with names no other run uses, so they run against the mock and against a backend database that keeps its data.
- **End-to-end assertions hold for both APIs:** they check what the app does, not texts that only one side produces, such as the TL;DR's wording.
- **End-to-end tests hold for any data:** the backend's database may be empty (in CI) or full of other posts, unlike the seeded mock. Find a control inside the part of the page it belongs to, such as `page.getByRole('navigation', { name: 'Account' })` for the header's links, because a name matches part of a longer name: on the whole page, `{ name: 'Write' }` also finds the empty list's "Write the first one", and `{ name: 'Log in' }` a post titled "How I log in".
- **Errors the mock doesn't produce**, such as `EDIT_CONFLICT`, are set up in the test with `server.use(...)`.
- **Queries:** prefer `getByRole` with a name, then `getByLabelText`, then `getByText`. Check accessible descriptions for field errors (`toHaveAccessibleDescription`), which also proves that screen readers get them.
- **Server wording:** assert on the app's own messages, never on the server's `detail` or validation texts.

### Running the frontend tests

From `frontend/`:

```bash
npm test          # unit, component and page tests (Vitest)
npm run e2e       # end-to-end tests (Playwright); starts the app in mock mode itself
npm run e2e:backend  # the same tests against the backend, which must be running on port 8080
npm run check     # lint, formatting, types, the Vitest tests and the build, as CI runs them
```

`npm run test:watch` reruns the affected tests on every save. Before the first `npm run e2e`, install the browser once with `npx playwright install chromium`.

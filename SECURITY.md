# Security

## Supported versions

The project has no releases yet. Only the `main` branch is maintained.

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**. Do not open a public issue.

This is a personal project, so responses are best effort. You will get an answer as soon as possible, and a fix will be credited to you if you want.

## Security model

> **Status:** mostly design. Built so far: the database constraints that keep the bot and deleted accounts without a password, the account deletion rule in the `user` module, and registration, with bcrypt password hashing and input validation. The security configuration lets anyone call `POST /auth/register` and refuses every other request with `401`. Login, tokens and authorization come with the rest of the `auth` step.

### Authentication

- Passwords are hashed with bcrypt. They must be at least 8 characters and at most 72 bytes (bcrypt ignores anything longer, so longer passwords are rejected rather than silently cut).
- Login returns the same `401` response for an unknown email and a wrong password, and takes about the same time for both, so it does not reveal which emails are registered.
- Access tokens are JWTs signed with HS256, using a secret of at least 32 random bytes from configuration. They contain the user's ID (`sub`), the issue and expiry times, and the issuer. They expire after `blog.auth.token-ttl` (proposed: 1 hour).
- Tokens are sent in the `Authorization` header, never in cookies. The browser never attaches them by itself, so the stateless API needs no CSRF protection.
- The bot user and deleted accounts have no password and can never log in (database constraints guarantee it).
- Deleting an account needs its password, not just a valid token.
- The tokens of a deleted account stop working at once: every request with a token checks that its account is not deleted.

### Authorization

- All reads are public, except the current user's own data (`/me`, liked items, AI request status).
- Every write needs a valid token.
- Only the author can edit or delete a post or comment. Nobody can edit or delete bot comments, or the content of a deleted account.
- An AI request's status is visible only to the user who made it.

The full rules per endpoint are in [docs/API.md](docs/API.md).

### Account deletion

Deleting an account works the way Reddit does it (see [docs/DOMAIN.md](docs/DOMAIN.md#flows)):

| Removed or hidden | Kept |
|---|---|
| Email and password hash (deleted) | The username, never shown again and never reusable, so nobody can pose as the old account |
| The profile (`GET /users/{id}` returns `404`) | Posts and comments, with the author shown as "[deleted]" |
| The author's name and ID on all content | Likes, so counts don't change |
| Every token of the account | AI requests the account made |

The account's ID stays in the database, so its posts and comments are still linked to each other there, but the API never exposes that link.

### Input and output

- Every request body is validated (lengths and formats in [docs/API.md](docs/API.md#validation)).
- Content is stored and returned as plain text. The API never produces HTML, and clients must escape content when displaying it. This applies equally to AI output.
- SQL statements written by hand always use bind parameters.
- Error responses never contain stack traces or internal details.

### AI features

- **What is sent to the LLM provider:** the post's content (for a TL;DR), and for a reply the post plus the chain of comments above the target. All of it is public content already, but it is processed by the provider under the provider's terms.
- **Prompt injection:** users can write text that tries to instruct the model. Mitigations: user content is clearly delimited and the system prompt says to treat it as data; the model is called **without tools**, so it cannot take any action; output length is capped; output is stored as plain text. The worst case is a misleading or offensive TL;DR or bot reply, which is clearly marked as written by the bot.
- **Cost:** AI use is deliberately not limited. The safety net is a spending limit at the provider (see [docs/OPERATIONS.md](docs/OPERATIONS.md#cost)).

### Secrets

- The JWT secret, the database password and the provider API key come only from environment variables or a secret store. They are never committed and never logged.
- The `dev` profile has its own development JWT secret, which no other profile accepts.

### Transport

- The application must run behind HTTPS.
- Browsers may call the API only from the origins in `blog.cors.allowed-origins`.

## Known limitations

These are accepted for now and listed so they are not mistaken for oversights:

- **No login throttling or account lockout.** Password guessing is limited only by bcrypt's cost. A rate limit at the reverse proxy would help.
- **No token revocation or logout.** A stolen token stays valid until it expires.
- **No email verification.** Anyone can register with any address.
- **Registration reveals** whether an email or username is already in use (`409`).
- **AI use is not limited.** Any user can cause any number of LLM calls; only the provider's spending limit caps the cost.
- **No moderation** of user content or AI output.
- **Deleting an account keeps its content.** Posts and comments stay, shown as "[deleted]", and users must delete them first if they want them gone. If the app serves people protected by privacy laws such as the GDPR, removing content on request may need a separate process.
- **No data export.**

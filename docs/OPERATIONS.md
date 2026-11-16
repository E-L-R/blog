# Operations

> **Status:** built, except for deployment. The configuration, profiles, health checks and metrics below exist, and the app runs locally (see [DEVELOPMENT.md](DEVELOPMENT.md)). The hosting platform is not chosen yet, and structured logging is still *planned*.

## Runtime requirements

- JDK 25, or the container image built with `./mvnw spring-boot:build-image`
- PostgreSQL (version 18 in development and tests)
- An Anthropic API key (`ANTHROPIC_API_KEY`), unless the fake LLM is enabled
- HTTPS in front of the application (a load balancer or reverse proxy). The app itself serves plain HTTP, and access tokens must never travel unencrypted.

## Running locally

```bash
./mvnw spring-boot:run
```

Docker must be running. The command turns on the `dev` profile (see `pom.xml`), and Spring Boot's Docker Compose support starts PostgreSQL from `compose.yaml` and connects the app to it, so no database settings are needed. It is active only when the app runs from Maven or the IDE: the tests use Testcontainers instead, and the packaged jar leaves it out. Details are in [DEVELOPMENT.md](DEVELOPMENT.md#run-the-app).

## Profiles

| Profile | Used for | Notable settings |
|---|---|---|
| (none) | Production and staging | Everything comes from environment variables. Startup fails if a required secret is missing. |
| `dev` | Local development (`application-dev.properties`) | A development JWT secret that only this profile accepts, the fake LLM, CORS open to local frontends on ports 3000 and 5173. PostgreSQL comes from Docker Compose, with or without this profile. |
| `test` | Automated tests (`src/test/resources/application-test.properties`) | A test JWT secret, the fake LLM, worker scheduling off, a 2-second LLM timeout. PostgreSQL comes from Testcontainers, and the AI tests replace the clock with one they move forward (see [TESTING.md](TESTING.md)) |

## Configuration

The defaults are in `src/main/resources/application.properties`, or in the properties records (`AuthProperties`, `CorsProperties`, `AiProperties`).

Every property can be set as an environment variable using Spring's relaxed binding: `blog.ai.worker.max-attempts` becomes `BLOG_AI_WORKER_MAX_ATTEMPTS`.

| Property | Default | Meaning |
|---|---|---|
| `blog.auth.jwt.secret` | none (required) | **Secret.** Base64-encoded HS256 key of at least 32 random bytes (`openssl rand -base64 32`). Every instance must use the same one. Startup fails if it is missing, too short, or the `dev` profile's secret outside that profile. |
| `blog.auth.token-ttl` | `1h` | Lifetime of an access token |
| `blog.cors.allowed-origins` | empty | Browser origins allowed to call the API, comma-separated |
| `blog.ai.fake` | `false` | Use the fake LLM client instead of a real provider |
| `blog.ai.worker.enabled` | `true` | Run the AI worker on this instance |
| `blog.ai.worker.poll-interval` | `2s` | Delay between polls for due jobs |
| `blog.ai.worker.concurrency` | `4` | Jobs claimed per poll, all processed in parallel |
| `blog.ai.worker.lease` | `2m` | How long a claim lasts before another worker may take the job. Must be longer than `blog.ai.llm.timeout`; startup fails otherwise. |
| `blog.ai.worker.max-attempts` | `5` | Attempts before a job is `FAILED` |
| `blog.ai.worker.initial-backoff` | `30s` | Wait before the first retry; doubles after each attempt |
| `blog.ai.llm.model` | `claude-opus-5-5` | The Claude model that writes TL;DRs and replies |
| `blog.ai.llm.effort` | `medium` | How much the model thinks before answering: `low`, `medium`, `high`, `xhigh` or `max` |
| `blog.ai.llm.timeout` | `60s` | Timeout of one LLM call |
| `blog.ai.llm.max-output-tokens` | `4096` | The most tokens one answer may use, thinking included. The prompts ask for short answers; an answer cut off at this limit fails. |
| `blog.ai.prompt.max-input-chars` | `20000` | Input size budget for a prompt |
| `blog.ai.prompt.max-answer-chars` | `10000` | Longer answers are cut; the same as the longest comment |

Standard Spring Boot properties are used for the rest:
- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` (**secret**)
- `ANTHROPIC_API_KEY` (**secret**), read by the Anthropic SDK. Without it, the app starts but logs a warning, and every AI call fails with `401` (the jobs end `FAILED`). The fake LLM (`blog.ai.fake=true`) needs no key.
- `management.*` for Actuator (see below).

Secrets come only from environment variables or the platform's secret store. They are never committed, and never logged.

## Health and monitoring

**Health:** Spring Boot Actuator exposes `/actuator/health`, with liveness and readiness groups (`/actuator/health/liveness`, `/actuator/health/readiness`). Readiness includes the database. No other Actuator endpoint is exposed over HTTP. The metrics below are recorded with Micrometer; publishing them needs a registry for the monitoring system (for example `micrometer-registry-prometheus`), added with the hosting decision.

**Metrics** (Micrometer), besides the standard HTTP, JVM and connection-pool metrics:

| Metric | Type | Tags |
|---|---|---|
| `blog.ai.queue.size` | gauge | `type`, `status` (`PENDING`, `IN_PROGRESS`) |
| `blog.ai.requests.finished` | counter | `type`, `status` (`COMPLETED`, `FAILED`, `CANCELLED`) |
| `blog.ai.llm.duration` | timer | `type`, `outcome` |

**What to watch:**
- `PENDING` jobs growing steadily: no worker is running, or the provider is down.
- A rising share of `FAILED` jobs: provider errors, or a bad prompt or model setting.
- LLM duration close to the timeout: raise the timeout and the lease together.
- A sudden rise in finished jobs: someone may be using the AI heavily (see [Cost](#cost)).

## Cost

AI use is not limited in the application (see [DECISIONS.md](DECISIONS.md#d9-ai-use-is-not-limited)): every post gets a TL;DR, and users can ask for as many AI replies as they like. The safety net is outside the app: set a monthly spending limit with the LLM provider, and watch `blog.ai.requests.finished`.

## Logging

*Planned. Today the app writes Spring Boot's default text logs.*

- Structured JSON logs outside `dev` (Spring Boot structured logging); readable text in `dev`.
- Log lines about a job include the AI request ID. Log lines about an HTTP request include a request ID.
- Never logged: passwords, tokens, the JWT secret, API keys. Post and comment content and prompts are not logged by default either: they are user content and can be large. Log IDs instead.

## Running several instances

- The application is stateless. Any number of instances can run behind a load balancer, as long as they share the database and the JWT secret.
- The worker runs on every instance where it is enabled. `SKIP LOCKED` and leases make that safe.
- The number of parallel LLM calls is instances × `blog.ai.worker.concurrency`. Keep it within the provider's rate limits.
- Deleting an account takes effect on every instance at once, because each request with a token checks the account in the database.

## Failure modes

| Failure | Effect | Recovery |
|---|---|---|
| LLM provider down or slow | Posts, comments and likes keep working. TL;DRs stay `PENDING` and AI replies wait. | Jobs retry with backoff, then become `FAILED`. A content edit queues a new TL;DR; users can ask for a reply again. |
| Instance crashes during a job | The job stays `IN_PROGRESS` until its lease expires | Another worker claims it again |
| Shutdown during a job | In-flight HTTP requests finish (graceful shutdown); a job still running at the end is abandoned | Its lease expires and another worker claims it |
| Database unavailable | Readiness fails, requests fail | The connection pool reconnects when the database is back |
| Like counts drift (a bug) | Wrong counts | Recompute `like_count` from the like tables; the like rows are the source of truth |
| A migration fails | The application does not start | Fix it with a new migration (applied ones are never edited) |

## Database maintenance

- Migrations run at startup (in use today). When several instances start at once, Flyway's lock makes them run only once.
- Backups depend on the hosting platform. The plan is a managed PostgreSQL with point-in-time recovery.
- `ai_requests` grows without limit. A retention job for old, finished requests is future work.

## Deployment

Not decided yet (see [DECISIONS.md](DECISIONS.md#open-decisions)). Whatever the platform, it needs to provide: PostgreSQL, environment variables or a secret store, HTTPS termination, and health checks against `/actuator/health/liveness` and `/actuator/health/readiness`.

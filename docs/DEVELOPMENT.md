# Development Guide

How to build, run and test the project on your own machine, and how to add a feature module. The workflow for branches, commits and pull requests is in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Prerequisites

- **JDK 25.** CI uses Eclipse Temurin.
- **Docker**, running. The tests start PostgreSQL with Testcontainers, and local runs start it with Docker Compose.
- No Maven installation is needed: the Maven wrapper (`./mvnw`) downloads the right version.

## Build and test

```bash
./mvnw verify
```

This compiles the code, runs every test against a real PostgreSQL in Docker, and builds the jar in `target/`. CI runs the same command on every pull request and every push to `main` (see [ci.yml](../.github/workflows/ci.yml)).

To run one test class:

```bash
./mvnw test -Dtest=PostModuleTests
```

One error message in the test output is expected: `duplicate key value violates unique constraint "users_username_lower_key"`. `UserModuleTests.onlyOneOfTwoSimultaneousRegistrationsGetsTheUsername` causes it on purpose: two registrations race for one username, and the test passes because the database refuses the second one.

The test strategy and conventions are in [TESTING.md](TESTING.md).

## Run the app

```bash
./mvnw spring-boot:run
```

This starts the app with the `dev` profile (`src/main/resources/application-dev.properties`), which supplies a development JWT secret. On startup:
1. Spring Boot finds `compose.yaml` and runs `docker compose up`, which starts a PostgreSQL 18 container. The database, user and password are all `blog`. Spring Boot connects the app to it, so no database settings are needed.
2. Flyway applies the migrations in `src/main/resources/db/migration` that the database doesn't have yet.
3. Hibernate checks that the entities match the tables (`ddl-auto=validate`), and the app listens on port 8080.

To try the API, register a user, log in, and send the token with each request that needs one:

```bash
curl -i -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"email":"alice@example.com","username":"alice","password":"correct horse"}'

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"alice@example.com","password":"correct horse"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -i -X POST localhost:8080/posts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"Hello","content":"My first post"}'
curl -s localhost:8080/posts
```

Registering answers `201` with the new account; sending the same request again gives `409` with the code `EMAIL_TAKEN`. Creating a post answers `201` with its URL in the `Location` header. The token is valid for an hour. Every endpoint is described in [API.md](API.md).

To run the app without the `dev` profile, as in production, set the secret yourself: `BLOG_AUTH_JWT_SECRET=$(openssl rand -base64 32)`. See [OPERATIONS.md](OPERATIONS.md#configuration).

Stop the app with Ctrl+C. Spring Boot then stops the container but keeps it, with its data, so the next start reuses them.

Docker Compose names things after the project folder: the project is called `blog`, and the container `blog-postgres-1`.

### Look inside the local database

While the app is running:

```bash
docker compose exec postgres psql -U blog
```

This opens `psql` inside the container, so you don't need it installed. For example, `\dt` lists the tables, `SELECT * FROM flyway_schema_history;` shows which migrations have run, and `\q` quits.

To connect from IntelliJ or another database tool, use host `localhost`, database `blog`, user `blog`, password `blog`, and the port that this command prints:

```bash
docker compose port postgres 5432
```

The port can change whenever the container starts, because `compose.yaml` publishes port 5432 without choosing the port on your machine.

### Reset the local database

```bash
docker compose down -v
```

This deletes the container **and all its data** (`-v` removes its volume). The next start creates an empty database and runs every migration again. Run it in the project folder, so it only touches the `blog` project.

You need this after changing a migration that your local database has already applied, for example while you are still working on a new one. Flyway stores a checksum of every migration it runs, and refuses to start when a file no longer matches.

## Project layout

```
.
├── src/main/java/io/github/elr/blog/
│   ├── BlogApplication.java              the application class; declares common as a shared module
│   ├── common/                           shared infrastructure (the Clock bean, error handling, web helpers)
│   ├── user/                             users, the bot user, deleted accounts, public profiles
│   ├── post/                             posts and their endpoints
│   ├── comment/                          comment threads
│   └── auth/                             registration, login, account deletion, the security configuration
├── src/main/resources/
│   ├── application.properties            defaults for every environment
│   ├── application-dev.properties        the dev profile, for ./mvnw spring-boot:run
│   └── db/migration/                     Flyway migrations
├── src/test/java/io/github/elr/blog/
│   ├── ModularityTests.java              module boundaries and diagrams
│   ├── BlogApplicationTests.java         the whole application starts
│   ├── TestcontainersConfiguration.java  the PostgreSQL container for tests
│   ├── common/                           the error advice, without Spring
│   ├── user/                             UserModuleTests, UserApiTests
│   ├── post/                             PostModuleTests, PostApiTests
│   ├── comment/                          CommentModuleTests, CommentApiTests
│   └── auth/                             AuthModuleTests, JwtSecretTests
├── src/test/resources/
│   └── application-test.properties       the test profile
├── compose.yaml                          PostgreSQL for local runs
├── .github/workflows/ci.yml              CI
└── docs/                                 design documents and this guide
```

How the modules fit together is in [ARCHITECTURE.md](ARCHITECTURE.md).

## Module diagrams

Every test run writes PlantUML diagrams of the modules to `target/spring-modulith-docs/` (see [ARCHITECTURE.md](ARCHITECTURE.md#generated-docs)). To view one, open the `.puml` file in a PlantUML viewer, such as the PlantUML Integration plugin for IntelliJ.

## Adding a feature module

The `user`, `post` and `comment` modules were built in these steps. Follow them for `like` and `ai`, using the design in [DOMAIN.md](DOMAIN.md) and [DATABASE.md](DATABASE.md):

1. **Migration:** a new `V<next number>__<description>.sql`. Name every constraint and index, and index foreign key columns (see [DATABASE.md](DATABASE.md#conventions)).
2. **Entity:** package-private, with no setters. Its constructor and methods enforce the rules, and it refers to other modules' entities by ID only (see [ARCHITECTURE.md](ARCHITECTURE.md#conventions)).
3. **Repository:** a package-private interface that extends `JpaRepository`.
4. **Read model:** a public record, such as `PostView`, with a package-private `of(entity)` method.
5. **Exceptions:** one public `RuntimeException` subclass per failure. If the API reports the failure, extend the matching base class in `common.error`, such as `ConflictException` (see [ARCHITECTURE.md](ARCHITECTURE.md#error-handling)).
6. **Service:** the module's public entry point. `@Transactional(readOnly = true)` on the class, `@Transactional` on each method that writes, and the time from the `Clock`.
7. **`package-info.java`:** `@ApplicationModule` with a `displayName` and the `allowedDependencies` from the [module table](ARCHITECTURE.md#modules). `common` is never listed.
8. **Endpoints:** a package-private controller in `web/`, with request and response records in `web/dto/`. Mark request bodies `@Valid`, take the caller's ID from a `@CurrentUser UUID` parameter, and return lists as `PageResponse`. `GET` endpoints are public and everything else needs a token; a different rule goes in `auth`'s `SecurityConfiguration`. The `post` module shows the pattern.
9. **Module test:** `<Name>ModuleTests`, with `@ApplicationModuleTest`, `@Import(TestcontainersConfiguration.class)` and `@ActiveProfiles("test")`. Add `mode = BootstrapMode.DIRECT_DEPENDENCIES` if the module uses another module. To test endpoints, add `extraIncludes = "auth"` (for the security configuration) and `@AutoConfigureMockMvc`, and put the HTTP tests in `<Name>ApiTests` with the same annotations, so both classes share one application context. Cover the module's rules and its [required tests](TESTING.md#required-tests), and prove the important database constraints with plain SQL through `JdbcTemplate`.
10. **Docs:** update the documents listed in [Keeping the docs current](../CONTRIBUTING.md#keeping-the-docs-current), the README roadmap and the CHANGELOG.
11. **Check:** `./mvnw verify` passes.

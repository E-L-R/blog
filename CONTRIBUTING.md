# Contributing

> **Status:** early development. Code follows the [roadmap](README.md#roadmap), one step at a time.

## Before you start

- Read the [README](README.md), [ARCHITECTURE.md](docs/ARCHITECTURE.md) and [DOMAIN.md](docs/DOMAIN.md). [DEVELOPMENT.md](docs/DEVELOPMENT.md) explains how to build, run and test the project.
- A step from the roadmap can start right away. A change to the design starts with an issue that says what will change and why, so it can be discussed first.

## Workflow

1. Create a branch from an up-to-date `main`, named `<type>/<short-description>`, for example `feat/post-module` or `docs/api-errors`. The types are the commit types below.
2. Keep each pull request to one topic.
3. Run the full build before pushing (Docker must be running):

   ```bash
   ./mvnw verify
   ```

4. Push the branch and open a pull request against `main`. Describe what changed and why; the [pull request template](.github/pull_request_template.md) has a checklist. CI must pass before merging.
5. Merge with **Create a merge commit**. Every commit on the branch then lands on `main` unchanged, so each one must follow the commit format below, and so should the pull request title.
6. After merging, update your local `main` and delete the branch:

   ```bash
   git switch main
   ```

   ```bash
   git pull --prune
   ```

   ```bash
   git branch -d <branch>
   ```

## Commit messages

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(optional-scope): short description in imperative mood
```

- **Description:** imperative mood ("add", not "added" or "adds"), lower case, no period at the end, at most 72 characters for the whole line.
- **Body (optional):** explain why the change was made, not what the diff shows. Wrap at 72 characters.
- **Breaking changes:** add `!` after the type or scope, and a `BREAKING CHANGE:` footer that explains what changed. Mark them even before version 1.0.

| Type | Use for |
|---|---|
| `feat` | A new feature or endpoint |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `test` | Adding or fixing tests only |
| `refactor` | A code change that neither fixes a bug nor adds a feature |
| `perf` | A performance improvement |
| `build` | The build or dependencies (`pom.xml`, Maven wrapper) |
| `ci` | GitHub Actions workflows |
| `chore` | Anything else that doesn't touch the application, such as editor settings |

**Scopes:** a module name (`user`, `auth`, `post`, `comment`, `like`, `ai`, `common`), `db` for migrations, or `deps` for dependency updates. Leave the scope out when a change spans several areas.

Examples:

```
feat(post): add post editing with optimistic locking
fix(like): keep like count unchanged when liking twice
build(deps): add spring modulith
docs: describe the rest api
feat(comment)!: return comment threads as flat lists
```

## Definition of done

A change is ready to merge when:

- It follows the layer rules and conventions in [ARCHITECTURE.md](docs/ARCHITECTURE.md).
- The tests listed for the feature in [TESTING.md](docs/TESTING.md#required-tests) exist and pass, along with `ModularityTests`.
- Schema changes are in a **new** migration; applied migrations are never edited.
- The documentation is updated in the same pull request (see below).
- Notable changes have an entry under `Unreleased` in the [CHANGELOG](CHANGELOG.md): every finished roadmap step, and anything API users would notice.

## Keeping the docs current

| When you change | Update |
|---|---|
| An entity, rule or flow | [DOMAIN.md](docs/DOMAIN.md) |
| A table, column, constraint or index | [DATABASE.md](docs/DATABASE.md), and add a migration |
| An endpoint, request, response or error code | [API.md](docs/API.md) |
| A module, dependency, event or the worker | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| A configuration property, metric or operational behaviour | [OPERATIONS.md](docs/OPERATIONS.md) |
| How to build, run or test the project | [DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| A significant design choice | [DECISIONS.md](docs/DECISIONS.md) |
| Anything security-related | [SECURITY.md](SECURITY.md) |
| A finished roadmap step | The [roadmap](README.md#roadmap), and the *planned* notes in the documents above |

## Code style

- Follow the conventions in [ARCHITECTURE.md](docs/ARCHITECTURE.md#conventions) and, for tests, [TESTING.md](docs/TESTING.md#conventions).
- Use records for DTOs, read models and events.
- No formatter is enforced yet (it is an [open decision](docs/DECISIONS.md#open-decisions)). Follow the style of the surrounding code.
- Lombok is fine for constructors and getters, but never `@Data`, `@EqualsAndHashCode` or `@ToString` on entities.
- Every file ends with a line break. In IntelliJ, turn on *Settings → Editor → General → On Save → Ensure every saved file ends with a line break*.

### Comments

- Every class gets a short Javadoc saying what it is for and where it fits: its module, and whether it is part of the module's API.
- Public service methods get Javadoc for anything the signature doesn't say: rules they enforce, and the exceptions they throw (`@throws`).
- Inline comments explain *why*, not *what*: a non-obvious choice, a trap, or a rule enforced somewhere else (a constraint, another module). Don't comment code that already says what it does, such as getters, constructors or record fields.
- Write comments about what the code does today. Planned work belongs in the docs, where it is marked *planned*; a comment about it goes stale as soon as it is built.
- Keep comments in step with the code: a change that makes a comment wrong updates it in the same commit.
- Never add a comment to a migration that is on `main`. Flyway checksums applied migrations, so any edit breaks existing databases.

## Security issues

Do not report vulnerabilities in public issues. See [SECURITY.md](SECURITY.md).

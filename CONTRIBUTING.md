# Contributing to Blog API

Thank you for contributing to the Blog API project! Please review this guide to understand our branch conventions, commit standards, local development workflows, and testing practices.

---

## 📋 Prerequisites
- **Java**: Eclipse Temurin JDK 25 or later (`java -version`)
- **Maven**: 3.9+ (always use the included `./mvnw` wrapper)
- **Docker**: Docker Desktop or Docker daemon running (required for Testcontainers & Docker Compose)

---

## 🌿 Git & Branching Strategy

We follow a GitHub Flow branch strategy with trunk-based development on `main`. All changes must be made in feature branches and merged via Pull Requests.

### Branch Naming Conventions
Format: `<type>/<issue-number>-<short-description>`

Examples:
- `feat/12-create-post-endpoint`
- `fix/45-jwt-token-expiration-check`
- `chore/8-repository-conventions`
- `docs/9-architecture-decision-records`
- `test/24-user-service-integration-tests`

---

## 💬 Conventional Commits

We enforce the [Conventional Commits](https://www.conventionalcommits.org/) standard for all commit messages and Pull Request titles.

### Format
```text
<type>(<scope>): <short description> (#<issue-number>)
```

### Allowed Types
| Type | Description |
| :--- | :--- |
| `feat` | New functionality or API endpoint |
| `fix` | Bug fix |
| `refactor` | Code refactoring without behavioral changes |
| `perf` | Performance improvement |
| `test` | Adding or updating tests |
| `chore` | Build tools, dependencies, or repository housekeeping |
| `ci` | GitHub Actions workflows and CI/CD pipelines |
| `docs` | Documentation and Markdown files |

### Common Scopes
- `posts`, `users`, `auth`, `comments`, `tags`, `ai`, `config`, `infra`, `deps`

### Examples
- `feat(posts): implement create post endpoint with tag validation (#15)`
- `fix(auth): return 401 Unauthorized for expired JWT tokens (#22)`
- `chore(deps): bump org.testcontainers:postgresql from 1.20.5 to 1.20.6 (#31)`

---

## 🛠️ Local Development

### Option 1: Docker Compose (Dedicated Local Database)
Start PostgreSQL on port `5432`:
```bash
docker compose up -d
./mvnw spring-boot:run
```

### Option 2: Testcontainers (Zero-Setup Development)
Runs the application with a dynamic, disposable PostgreSQL container managed directly by Testcontainers:
```bash
./mvnw spring-boot:test-run
```

---

## 🎨 Code Formatting & Quality
We enforce strict formatting rules via Spotless (`palantir-java-format` for Java and `sortpom` for POM files) and the Maven Enforcer Plugin.

- **Check formatting:**
  ```bash
  ./mvnw spotless:check
  ```
- **Apply formatting automatically:**
  ```bash
  ./mvnw spotless:apply
  ```

---

## 🧪 Testing & Verification
Before pushing changes or opening a pull request, run the full verification lifecycle:

- **Run unit tests only:**
  ```bash
  ./mvnw test
  ```
- **Run full test suite (Spotless, Enforcer, Unit Tests, Testcontainers Integration Tests, JaCoCo Coverage Gate):**
  ```bash
  ./mvnw clean verify
  ```

---

## 🚀 Pull Request Lifecycle

1. Ensure your branch is rebased on latest `main`:
   ```bash
   git checkout main
   git pull origin main
   git checkout your-feature-branch
   git merge main
   ```
2. Run `./mvnw clean verify` and `./mvnw spotless:check`.
3. Push your branch and open a Pull Request targeting `main`.
4. Fill in the Pull Request template and link the issue using `Closes #<issue-number>`.
5. Verify that all required status checks pass:
  - `Build, Test & Verify`
  - `Analyze (Java)` (CodeQL)
  - `Review Dependencies` (Dependency Review)
6. Merges to `main` are performed strictly as **Squash Merges** to maintain a clean linear git history.

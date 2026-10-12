## 📌 Description
<!-- Provide a brief summary of the changes introduced by this pull request. -->

## 🔗 Related Issue
<!-- Link the related issue using GitHub keywords (e.g., Closes #123, Fixes #456) -->
Closes #

## 🏷️ Type of Change
<!-- Select all that apply using [x] -->
- [ ] 🚀 `feat`: New feature or capability
- [ ] 🐛 `fix`: Bug fix
- [ ] ♻️ `refactor`: Code restructuring without behavior changes
- [ ] 🧪 `test`: New or modified tests
- [ ] ⚙️ `chore`: Build, tooling, dependencies, or housekeeping
- [ ] 📚 `docs`: Documentation updates
- [ ] 🛡️ `ci`: CI/CD workflow updates

## 📋 Pre-Merge Checklist
<!-- Ensure all checks pass before requesting review / merging -->
- [ ] Code follows project formatting: `./mvnw spotless:check` passes
- [ ] Full test suite passes: `./mvnw clean verify` (unit & Testcontainers tests)
- [ ] JaCoCo code coverage requirement is satisfied (>= 70%)
- [ ] PR title adheres to [Conventional Commits](https://www.conventionalcommits.org/) (e.g., `feat(posts): add create post endpoint (#123)`)
- [ ] Documentation / README / ADR updated if applicable

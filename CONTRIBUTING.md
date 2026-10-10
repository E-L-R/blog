# Contributing to Blog API

Thank you for contributing! Please follow the build and style conventions outlined below.

## Prerequisites
- JDK 25 or later
- Maven 3.9+ (use the bundled `./mvnw` wrapper)
- Docker (required for Testcontainers integration tests)

## Code Formatting & Quality
We use Spotless with palantir-java-format and sortpom to keep all code and POM files consistently formatted.

- Check formatting:
  ./mvnw spotless:check

- Auto-format code and POM:
  ./mvnw spotless:apply

## Build & Validation
Before submitting a pull request, ensure the complete build passes (including format checks, enforcer rules, and tests):
./mvnw clean verify


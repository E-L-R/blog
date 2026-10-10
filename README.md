# Blog API

Modern Spring Boot REST API for a technical blog with Spring AI TL;DR generation.

## Local Development

You can run the application locally using either Docker Compose or Testcontainers.

### Option 1: Run with Docker Compose
Spring Boot automatically detects `compose.yaml` and starts PostgreSQL 17 on port 5432:
./mvnw spring-boot:run

Alternatively, start PostgreSQL manually in the background before running:
docker compose up -d
./mvnw spring-boot:run

### Option 2: Run with Testcontainers (Zero Local DB Setup)
Starts a dynamic, disposable PostgreSQL container managed directly by Testcontainers:
./mvnw spring-boot:test-run

## Running Tests
- Fast unit tests:
  ./mvnw test

- Full test suite with integration tests and coverage check:
  ./mvnw clean verify

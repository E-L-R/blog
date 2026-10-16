# Blog API (blog-backend)

[![Build](https://github.com/E-L-R/blog-backend/actions/workflows/build.yml/badge.svg)](https://github.com/E-L-R/blog-backend/actions/workflows/build.yml)

Spring Boot REST API for a technical blog. Each published post gets a short **TL;DR** written by a
language model we build ourselves from scratch in PyTorch, in
[`E-L-R/blog-tldr-model`](https://github.com/E-L-R/blog-tldr-model), and call over HTTP.

## Local Development

You can run the application locally using either Docker Compose or Testcontainers.

### Option 1: Run with Docker Compose
Spring Boot detects `compose.yaml` and starts PostgreSQL 17 on port 5432:
```bash
./mvnw spring-boot:run
```
Alternatively, start PostgreSQL in the background first:
```bash
docker compose up -d
./mvnw spring-boot:run
```

### Option 2: Run with Testcontainers (zero local DB setup)
```bash
./mvnw spring-boot:test-run
```

## Running Tests
```bash
./mvnw test          # fast unit tests
./mvnw clean verify  # everything: formatting, unit + integration tests, coverage gate
```

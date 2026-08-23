# URL Shortener — AI-Assisted Software Engineering Assignment

## About

A URL shortener service built as part of the "AI-Assisted Software Engineering System"
interview assignment. The service supports shortening, redirecting, click analytics,
trending URLs, and soft-deactivation, with reliability features (caching, async processing,
rate limiting, collision handling) built in.

This project follows **AI-assisted engineering execution**, not autonomous orchestration:
AI (Claude) was used as an accelerator for implementation and design-rationale discussion,
while all decisions, debugging, and quality ownership remained with the engineer. See
[`AI_USAGE.md`](src/main/java/com/assignment/urlshortener/docs/AI_USAGE.md) for the full traceability log of accepted, edited, and
rejected AI suggestions.

## Tech Stack

- Java 17, Spring Boot 3
- MySQL 8.0 (via Docker Compose)
- Spring Data JPA / Hibernate
- Caffeine (in-memory caching, rate limiting)
- Spring `@Async` (background analytics processing)
- JUnit 5 + Mockito + MockMvc (testing, H2 in-memory DB)

## Features

| Feature | Endpoint |
|---|---|
| Shorten a URL | `POST /api/urls` |
| Redirect | `GET /{shortCode}` |
| Click stats for a URL | `GET /api/urls/{shortCode}/stats` |
| Trending URLs (last 24h) | `GET /api/urls/trending?limit=10` |
| Deactivate a URL | `PATCH /api/urls/{shortCode}/deactivate` |

## Prerequisites

- Java 17
- Maven
- Docker Desktop (running)

## Setup & Run

1. Ensure Docker Desktop is running.
2. From the project root:
   ```bash
   mvn spring-boot:run
   ```
   This automatically:
   - Spins up a MySQL container (`docker-compose.yml`)
   - Creates the `urlshortener` database
   - Runs `schema.sql` to create tables
   - Starts the app on `http://localhost:8080`

3. Verify:
   ```bash
   curl -X POST http://localhost:8080/api/urls \
     -H "Content-Type: application/json" \
     -d '{"originalUrl": "https://example.com"}'
   ```

## Configuration

`application.properties`:
```properties
app.base-url=http://localhost:8080/
app.rate-limit.max-requests-per-minute=20
```

## Running Tests

```bash
mvn test
```
Runs against an in-memory H2 database (`application-test.properties`) — no Docker
dependency for tests.

## Manual End-to-End Testing

A full manual test checklist (15 scenarios covering happy paths, error handling, caching,
async behavior, rate limiting, and the brownfield cache-eviction fix) is in
[`E2E_TEST_CASES.md`](src/main/java/com/assignment/urlshortener/docs/E2E_TEST_CASES.md).

Quick smoke test:
```bash
# 1. Shorten
curl -X POST http://localhost:8080/api/urls -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://google.com"}'

# 2. Redirect (use shortCode from step 1 response)
curl -i http://localhost:8080/{shortCode}

# 3. Check stats
curl http://localhost:8080/api/urls/{shortCode}/stats
```

## Project Documentation

| Doc | Contents |
|---|---|
| [`ARCHITECTURE.md`](src/main/java/com/assignment/urlshortener/docs/ARCHITECTURE.md) | Components, request flows, design decisions & rationale, scaling notes |
| [`SETUP.md`](src/main/java/com/assignment/urlshortener/docs/SETUP.md) | Detailed setup/run/stop instructions |
| [`AI_USAGE.md`](src/main/java/com/assignment/urlshortener/docs/AI_USAGE.md) | AI-assisted engineering traceability (accepted/edited/rejected suggestions) |
| [`AMBIGUOUS_SCENARIO.md`](src/main/java/com/assignment/urlshortener/docs/AMBIGUOUS_SCENARIO.md) | How the ambiguous "trending URLs" requirement was interpreted and resolved |
| [`ENGINEERING_SUMMARY.md`](src/main/java/com/assignment/urlshortener/docs/ENGINEERING_SUMMARY.md) | Final plan, artifacts, risks/trade-offs, assumptions, limitations |
| [`E2E_TEST_CASES.md`](src/main/java/com/assignment/urlshortener/docs/E2E_TEST_CASES.md) | Full manual end-to-end test checklist |

## Key Design Decisions (summary — full rationale in ARCHITECTURE.md)

- **Random 7-character short codes** (not sequential) — avoids URL enumeration; requires
  bounded collision-retry (max 5 attempts)
- **Caffeine over Redis** — no external infra needed at this scale
- **`@Async` over Kafka/Event Hubs** — non-blocking analytics without message-broker overhead
- **Denormalized `click_count`** — fast stat reads at the cost of a small write-side increment
- **Soft-deactivation (`active` flag)** with explicit **cache eviction** — preserves analytics
  history; eviction was a brownfield fix required because deactivation mutates already-cached
  data

## Known Limitations

- No authentication/authorization — all endpoints public, analytics/trending are global
- Rate limiting is per-instance, in-memory (not distributed-safe)
- Trending is computed on-demand, not precomputed/scheduled
- Schema managed via plain `schema.sql`, not Flyway/Liquibase

See [`ENGINEERING_SUMMARY.md`](src/main/java/com/assignment/urlshortener/docs/ENGINEERING_SUMMARY.md) for the complete list with
mitigation/scaling notes.

## Stopping the App

```bash
docker-compose down       # stop container, keep data
docker-compose down -v    # stop container, wipe data (fresh schema.sql on next start)
```

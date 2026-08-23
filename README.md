# URL Shortener — AI-Assisted Software Engineering Assignment

**Author:**Sandeep | **Assignment:** AI-Assisted Software Engineering System — URL Shortener
**Principle followed:** AI assists within tasks; the engineer owns execution, correctness, and
final quality.

A URL shortener service with core APIs, click analytics, trending, and reliability features
(caching, async processing, rate limiting, collision handling), built over 2–3 days with AI
(Claude) as an engineering accelerator — not an autonomous system. Every design decision below
has a stated rationale and, where relevant, a rejected alternative.

---

## Table of Contents
- [How This Maps to the Assignment Rubric](#how-this-maps-to-the-assignment-rubric)
- [Tech Stack](#tech-stack)
- [Architecture at a Glance](#architecture-at-a-glance)
- [Features & API Reference](#features--api-reference)
- [Setup & Run](#setup--run)
- [Testing](#testing)
- [Three Required Scenarios](#three-required-scenarios)
- [Key Design Decisions](#key-design-decisions)
- [Known Limitations](#known-limitations)
- [Full Documentation Index](#full-documentation-index)

---

## How This Maps to the Assignment Rubric

| Rubric Item | Where to Find It |
|---|---|
| Requirement Understanding | [`docs/GREENFIELD_SCENARIO.md`](docs/GREENFIELD_SCENARIO.md) §1 |
| Task Decomposition | Each scenario doc, §2 (dependency-ordered task breakdown) |
| Codebase Reasoning (Brownfield) | [`docs/BROWNFIELD_SCENARIO.md`](docs/BROWNFIELD_SCENARIO.md) §1 — impact analysis table |
| AI-Assisted Execution (critical differentiator) | [`docs/AI_USAGE.md`](docs/AI_USAGE.md) — full traceability log |
| Engineering Output (code, tests, docs) | This repo + [`docs/`](docs/) folder |
| Validation & Risk Control | [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md) — risks/trade-offs table |
| Final Engineering Summary | [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md) |

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language / Framework | Java 17, Spring Boot 4 |
| Database | MySQL 8.0 (Docker Compose) |
| ORM | Spring Data JPA / Hibernate |
| Caching & Rate Limiting | Caffeine (in-memory) |
| Async Processing | Spring `@Async` + `ThreadPoolTaskExecutor` |
| Testing | JUnit 5, Mockito, MockMvc, H2 (in-memory) |

Full rationale for each choice (and what was rejected instead) is in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Architecture at a Glance

```
Client → UrlController / AnalyticsController → RateLimitInterceptor
                    ↓
              UrlService / AnalyticsService
                    ↓
     UrlMappingRepository (+ Caffeine cache)  →  MySQL (Docker)
                    ↓
     AnalyticsService.recordClickAsync()  →  background thread pool
                    ↓
              GlobalExceptionHandler → structured JSON errors
```

**Redirect (hot path):** cache-first lookup → 302 response → fire-and-forget async click log.
Click logging never blocks the redirect and never surfaces failures to the user.

Full component diagram, request flows, and reliability measures:
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Features & API Reference

| Feature | Endpoint | Example |
|---|---|---|
| Shorten a URL | `POST /api/urls` | see below |
| Redirect | `GET /{shortCode}` | `302` to original URL |
| Click stats | `GET /api/urls/{shortCode}/stats` | see below |
| Trending (last 24h) | `GET /api/urls/trending?limit=10` | see below |
| Deactivate a URL | `PATCH /api/urls/{shortCode}/deactivate` | `204 No Content` |

**Shorten — request/response:**
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com"}'
```
```json
{
  "shortUrl": "http://localhost:8080/aZ3kLm9",
  "originalUrl": "https://example.com",
  "expiresAt": null
}
```

**Stats — response:**
```json
{
  "shortCode": "aZ3kLm9",
  "totalClicks": 12,
  "recentClicks": [
    { "clickedAt": "2026-08-23T10:15:00", "referrer": null, "userAgent": "Mozilla/5.0" }
  ]
}
```

**Error responses** are always structured JSON, never raw stack traces:
```json
{ "timestamp": "...", "status": 404, "error": "Not Found", "message": "No URL found for short code: xyz" }
```
Status codes used: `400` invalid input, `404` not found, `409` alias taken, `410` expired/deactivated, `429` rate limited.

---

## Setup & Run

**Prerequisites:** Java 17, Maven, Docker Desktop (running)

```bash
mvn spring-boot:run
```
This single command automatically starts a MySQL container, creates the database, runs
`schema.sql`, and starts the app on `http://localhost:8080`. No manual DB setup needed.

Stop with `docker-compose down` (keeps data) or `docker-compose down -v` (wipes data, fresh
schema on next start). Full details: [`docs/SETUP.md`](docs/SETUP.md).

---

## Testing

```bash
mvn test
```
Runs the full automated unit + integration test suite (H2 in-memory, no Docker dependency) —
covers happy paths, collision retry, expiry, structured errors, and the brownfield
cache-eviction regression test. A handful of behaviors (async thread confirmation, cache-hit
log inspection, rate-limit load testing) are verified manually since they assert on log/timing
output rather than a response value — all clearly marked as such in
[`docs/E2E_TEST_CASES.md`](docs/E2E_TEST_CASES.md), which maps every scenario to its exact
`@Test` method.

---

## Three Required Scenarios

| Scenario | Summary | Doc |
|---|---|---|
| **Greenfield** | Full ground-up build: requirement analysis → decomposition → core APIs, analytics, reliability → tests | [`docs/GREENFIELD_SCENARIO.md`](docs/GREENFIELD_SCENARIO.md) |
| **Brownfield** | Added URL deactivation to the existing system; surfaced and fixed a real cache-staleness bug (`@CacheEvict` missing on the mutation path) | [`docs/BROWNFIELD_SCENARIO.md`](docs/BROWNFIELD_SCENARIO.md) |
| **Ambiguous** | Interpreted an underspecified "show trending URLs" request — resolved definitions for "trending" and "show," documented assumptions and rejected alternatives | [`docs/AMBIGUOUS_SCENARIO.md`](docs/AMBIGUOUS_SCENARIO.md) |

---

## Key Design Decisions

- **Random 7-character short codes** (not sequential) — prevents URL enumeration; requires
  bounded collision-retry (max 5 attempts, fails loud rather than hanging)
- **Caffeine over Redis** — no external infra needed at this scale; documented as a scaling
  path if the app needs to run across multiple instances
- **`@Async` over Kafka/Event Hubs** — non-blocking analytics without message-broker overhead;
  same reasoning as above for the scaling path
- **Denormalized `click_count`** — fast stat reads at the cost of a small write-side increment
- **Soft-deactivation (`active` flag) with explicit cache eviction** — preserves click history;
  the cache-eviction requirement was a real bug caught during the brownfield stage, not
  anticipated up front (see `BROWNFIELD_SCENARIO.md`)

Full rationale and rejected alternatives for each: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Known Limitations

- No authentication/authorization — all endpoints public, analytics/trending are global, not
  per-user
- Rate limiting is per-instance, in-memory — not safe across multiple app instances (would
  need Redis for horizontal scaling)
- Trending is computed on-demand, not precomputed/scheduled — fine at current data volume
- Schema managed via plain `schema.sql`, not Flyway/Liquibase

Full list with mitigation/scaling notes: [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md).

---

## Full Documentation Index

| Doc | Contents |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Components, request flows, design decisions & rationale, scaling notes |
| [`docs/SETUP.md`](docs/SETUP.md) | Detailed setup/run/stop instructions |
| [`docs/AI_USAGE.md`](docs/AI_USAGE.md) | AI-assisted engineering traceability (accepted/edited/rejected suggestions) |
| [`docs/GREENFIELD_SCENARIO.md`](docs/GREENFIELD_SCENARIO.md) | Ground-up build: requirement understanding, decomposition, execution, validation |
| [`docs/BROWNFIELD_SCENARIO.md`](docs/BROWNFIELD_SCENARIO.md) | Deactivate-URL enhancement, including the cache-eviction fix |
| [`docs/AMBIGUOUS_SCENARIO.md`](docs/AMBIGUOUS_SCENARIO.md) | How the ambiguous "trending URLs" requirement was interpreted and resolved |
| [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md) | Final plan, artifacts, risks/trade-offs, assumptions, limitations |
| [`docs/E2E_TEST_CASES.md`](docs/E2E_TEST_CASES.md) | Full test suite — scenario-to-`@Test` mapping, plus manual-only checks |
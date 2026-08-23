# Architecture Overview — URL Shortener Service

## 1. Tech Stack

| Layer | Choice                                     | Why |
|---|--------------------------------------------|---|
| Language/Framework | Java 17, Spring Boot 4                     | Matches existing production experience — faster build, stronger defense in review |
| Database | MySQL 8.0 (Docker)                         | Chosen over MS SQL Server for Docker familiarity and simpler reproducible setup |
| Caching | Caffeine (in-memory)                       | Zero external infra; satisfies low-latency redirect requirement without Redis setup overhead |
| Async processing | Spring `@Async` + `ThreadPoolTaskExecutor` | Non-blocking analytics writes without a message broker (Kafka/RabbitMQ ruled out — overkill for single-instance scope) |
| Rate limiting | Custom Caffeine-backed interceptor         | Reuses existing dependency instead of adding Bucket4j/Redis |
| Containerization | Docker Compose                             | Single-command reproducible setup (`docker-compose up`), auto-triggered by `spring-boot-docker-compose` |

## 2. High-Level Components

```
┌─────────────┐     ┌──────────────────┐     ┌────────────────┐
│   Client    │────▶│  UrlController   │────▶│   UrlService    │
└─────────────┘     │  Analytics       │     │   (Impl)        │
                     │  Controller      │     └────────┬────────┘
                     └──────────────────┘              │
                                                         ▼
                     ┌──────────────────┐     ┌────────────────┐
                     │ RateLimit        │     │ UrlMapping      │
                     │ Interceptor      │     │ Repository      │
                     └──────────────────┘     │ (+ Caffeine     │
                                               │   Cache Layer)  │
                     ┌──────────────────┐     └────────┬────────┘
                     │ Global Exception │              │
                     │ Handler          │              ▼
                     └──────────────────┘     ┌────────────────┐
                                               │  MySQL (Docker) │
                     ┌──────────────────┐     └────────────────┘
                     │ AnalyticsService │
                     │ (@Async thread   │◀────────────┘
                     │  pool)           │  (fire-and-forget on redirect)
                     └──────────────────┘
```

## 3. Request Flows

### Shorten (`POST /api/urls`)
1. Validate request (`@Valid` — URL format, blank checks)
2. If custom alias given → check uniqueness → use directly
3. Else → generate random 7-char Base62 code → check uniqueness → bounded retry (max 5) on collision
4. Persist `UrlMapping` (default `active = true`)
5. Return short URL

### Redirect (`GET /{shortCode}`)
1. `resolveShortCode()` — cache-first lookup (`@Cacheable`)
2. Reject if not found (404), expired (410), or deactivated (410)
3. Fire-and-forget async click logging (does not block response)
4. Return `302` with `Location` header

### Analytics (`GET /api/urls/{code}/stats`, `GET /api/urls/trending`)
- Reads from denormalized `click_count` (fast) and `click_event` detail rows
- Trending computed on-demand over a 24-hour rolling window (see `AMBIGUOUS_SCENARIO.md`)

### Deactivate (`PATCH /api/urls/{code}/deactivate`)
- Updates `active = false`
- **Explicitly evicts cache** (`@CacheEvict`) — required because this is the first mutation
  path on already-cached data (see `BROWNFIELD_SCENARIO` notes in Stage 8)

## 4. Key Design Decisions & Rationale

| Decision | Alternative Considered | Why Chosen |
|---|---|---|
| Random 7-char short codes | Base62-encoded auto-increment ID | Sequential IDs allow URL enumeration — random codes avoid exposing volume/order |
| Bounded retry (5) on collision | Unbounded retry loop | Fail loud and fast rather than risk hanging a request on a systemic bug |
| Caffeine over Redis | Redis-backed cache | No external infra needed; sufficient for single-instance scope |
| `@Async` over Kafka/Event Hubs | Message broker-based analytics pipeline | Avoids infra overhead not justified at this scale; noted as a scaling path in limitations |
| Denormalized `click_count` | Live `COUNT(*)` on `click_event` | Fast reads at the cost of a small write-side increment; consistency risk documented |
| Fixed-window rate limiting | Sliding window / token bucket library | Simpler implementation; boundary-burst limitation explicitly accepted and documented |
| Soft-deactivate (`active` flag) | Hard delete | Preserves click history/analytics; reversible action |

## 5. Reliability Measures

- Bounded retry on short-code generation collisions
- Global exception handler → structured JSON errors (404/409/410/429/400/500), never raw stack traces
- Async analytics failures are caught and logged, never propagate to the user-facing redirect
- Per-IP rate limiting (fixed window, 1 minute, configurable via `app.rate-limit.max-requests-per-minute`)
- Cache eviction on mutation to prevent stale-data bugs (deactivation case)

## 6. Known Limitations (stated explicitly, not hidden)

- No authentication/authorization — all endpoints are public; trending/analytics are global, not per-user
- Rate limiting is per-IP and in-memory — does not survive app restart, and does not work correctly across multiple app instances (would need a shared store like Redis for horizontal scaling)
- Trending calculation is computed on-demand, not precomputed/scheduled — acceptable at current scale, would need indexing/batch precomputation at higher volume
- Schema migrations are plain `schema.sql`, not Flyway/Liquibase — fine for this assignment, not production-grade migration tooling
- No pagination on `getStats`'s recent-clicks list (hardcoded limit of 20)

## 7. Scaling Notes (if this went to production)

- Replace in-memory rate limiter with Redis-backed counter for multi-instance deployments
- Replace `@Async` analytics with Kafka/Event Hubs for durability and multi-consumer processing (mirrors real production pattern already used in other systems worked on)
- Introduce Flyway/Liquibase for schema versioning
- Add authentication (JWT, matching prior project experience) to scope analytics/trending per user

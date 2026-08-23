# Scenario 1: Greenfield

## Requirement (as given)
> Build a URL shortener service from scratch with core APIs, analytics, and reliability
> features.

This is the foundational greenfield build — no existing codebase, no legacy constraints.

---

## 1. Requirement Understanding

**Normalized into concrete functional requirements:**
1. Shorten a long URL → short code (auto-generated, optionally custom alias)
2. Redirect short URL → original URL
3. Track analytics per short URL: click count, timestamp, referrer, user agent
4. Handle link expiration (TTL)
5. Reliability: prevent duplicate/colliding short codes, rate-limit abuse

**Non-functional requirements identified:**
- Low redirect latency (this is the hottest path — needs caching)
- Data integrity (guaranteed-unique short codes)
- Non-blocking analytics (must not slow down the user-facing redirect)

**Ambiguities resolved at the start (see full reasoning in earlier design discussion):**
- Short code generation strategy → random 7-character Base62 (chosen over sequential
  ID-encoding to prevent URL enumeration)
- Analytics write timing → async, not inline with redirect

---

## 2. Task Decomposition

```
T1: Project setup (Spring Boot, Docker Compose, MySQL, schema)         [no deps]
T2: UrlMapping entity + repository                                      [depends: T1]
T3: Shorten API (POST /api/urls)                                        [depends: T2]
T4: Redirect API (GET /{code})                                          [depends: T2]
T5: ClickEvent entity + async click logging                             [depends: T4]
T6: Analytics retrieval API (GET /api/urls/{code}/stats)                [depends: T5]
T7: Caching layer (Caffeine) for redirect lookups                       [depends: T4]
T8: Collision handling + rate limiting + global exception handling      [depends: T3, T4]
T9: Unit + integration tests                                            [depends: all above]
T10: Documentation (architecture, setup, AI-usage)                      [ongoing]
```

This decomposition was executed sequentially across build stages, with each stage compiled
and manually verified (via Postman/curl and log inspection) before moving to the next —
rather than generating the entire system at once and debugging retroactively.

---

## 3. Execution Summary

| Component | Outcome |
|---|---|
| Entities (`UrlMapping`, `ClickEvent`) | Built with JPA annotations, `@PrePersist` defaults |
| Shorten API | Random 7-char Base62 code, custom alias support, bounded collision retry (max 5 attempts) |
| Redirect API | `@Cacheable` lookup, 302 response, async click-logging trigger |
| Analytics | `@Async` background write via dedicated `ThreadPoolTaskExecutor`; denormalized `click_count` for fast reads |
| Reliability | Global exception handler (structured JSON errors), Caffeine-backed per-IP rate limiter, bounded retry on short-code collisions |
| Tests | Unit tests (Mockito) for service logic + integration tests (`MockMvc` + H2) for full request flow |

---

## 4. Validation

- Confirmed async behavior via thread-name logging (`analytics-*` vs. request thread)
- Confirmed cache behavior via absence of repeated SQL `SELECT` on repeat redirects
- Confirmed structured error responses for 400/404/409/410/429 — no raw stack traces exposed
- Load-tested rate limiter via Postman Collection Runner (25 iterations → 20×200, 5×429)
- Full `mvn test` suite passing against H2 in-memory DB

---

## 5. Key Design Trade-offs (specific to greenfield decisions)

| Decision | Alternative Considered | Why Chosen |
|---|---|---|
| Random short codes | Base62-encoded auto-increment ID | Avoids exposing sequential volume/order (enumeration risk) |
| Caffeine (in-memory) cache | Redis | No external infra needed at this scale |
| `@Async` in-process | Kafka / Azure Event Hubs | Avoids infra overhead not justified for a single-instance assignment |
| Denormalized `click_count` | Live `COUNT(*)` query | Fast reads at the cost of a small write-side increment |

Full rationale for each is in `ARCHITECTURE.md`; this doc focuses on how they emerged from the
greenfield requirement-understanding and decomposition process specifically.

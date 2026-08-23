# AI-Assisted Engineering Summary

## Project: URL Shortener Service
**Author:** Sandeep

**AI Tool Used:** Claude (Anthropic)

**Duration:** ~2-3 days

**Technology Stack:** Spring Boot 4.1, Java 17, MySQL, Docker Compose, Caffeine 

**Principle followed:** AI assists within tasks; the engineer owns execution, correctness, and final quality.

---

## 1. Approach to AI-Assisted Execution

Rather than asking AI to generate the whole project in one shot, the project was broken into
10 sequential stages (requirement analysis → setup → core domain → analytics → reliability →
AI-usage docs → tests → brownfield scenario → ambiguous scenario → final docs). Each stage
was:

1. Scoped with explicit intent and constraints before any code was generated
2. Reviewed for correctness before moving to the next stage
3. Run and manually verified (via Postman/logs) rather than assumed correct
4. Only progressed once the previous stage compiled and behaved as expected

This mirrors how the same task would be handled without AI — plan, implement, verify, iterate —
with AI accelerating the implementation and explanation steps, not replacing engineering
decisions.

---

## 2. Task Definition Pattern

Each stage was defined with:

- **Intent** — what capability we were adding (e.g. "async click logging that doesn't block
  redirect")
- **Constraints** — fixed tech stack (Spring Boot 3, Java 17, MySQL via Docker, Caffeine,
  no Kafka/Redis to keep scope realistic for 2-3 days)
- **Acceptance criteria** — code compiles, is testable via Postman, and every design choice
  has a stated rationale defensible in review

Example (Stage 4 — Analytics):
> Intent: Log every redirect click without adding latency to the redirect response.
> Constraint: No external message broker; single-instance deployment.
> Acceptance: Click write happens on a separate thread pool; redirect response time is
> unaffected; failures in logging must not surface to the user.

---

## 3. Traceability — Accepted / Edited / Rejected AI Suggestions

| # | AI Suggestion                                                                            | Status                                   | Rationale                                                                                                                                                                                                                                                                                    |
|---|------------------------------------------------------------------------------------------|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Base62-encode the DB auto-increment ID as the short code                                 | **Rejected (replaced)**                  | Sequential, predictable short codes allow enumeration of all shortened URLs — a real privacy/security concern. Replaced with random 7-character generation.                                                                                                                                  |
| 2 | `short_code VARCHAR(10) NULL UNIQUE` in schema `as NOT NULL UNIQUE`                       | **Rejected (replaced)**                  | Identified and fixed a schema issue** where the `shortCode` field was not defined as `NOT NULL`. Updated the database schema to enforce the constraint and ensure every URL mapping has a short code.                                                                                                    |
| 3 | `@Component` on `AsyncConfig` / cache & async setup                                      | **Edited**                               | `@Async` and `@Cacheable` were silently no-oping (methods running synchronously, no thread pool engaging). Diagnosed as needing `@Configuration` instead of `@Component` to correctly host `@EnableAsync`. Verified fix via thread-name logging (`analytics-3` appearing in console output). |
| 4 | Swallow exceptions inside async click-logging method, log only                           | **Accepted**                             | Background thread has no caller to propagate an exception to; logging + continuing preserves the primary user-facing redirect flow regardless of analytics failure.                                                                                                                          |
| 5 | Bounded retry (5 attempts) for short-code collision handling                             | **Accepted**                             | Unbounded retry risks hanging a request if generation is systemically broken. Bounded retry + explicit failure is a defensible reliability pattern (fail loud, fail fast).                                                                                                                   |
| 6 | Fixed-window rate limiting (Caffeine-backed, per-IP) instead of Bucket4j/Redis           | **Accepted, with documented limitation** | Avoids introducing a new dependency for one feature; reuses existing Caffeine dependency. Known limitation (burst at window boundary) explicitly documented rather than silently accepted.                                                                                                   |
| 7 | Denormalized `click_count` column on `url_mapping` instead of `COUNT(*)` on `click_event` | **Accepted**                             | Trades a small write-side increment for fast read-side stats; avoids full table scans on every stats request. Consistency risk if async write fails is a stated trade-off.                                                                                                                   |
| 8 | MS SQL Server as the database                                                            | **Rejected (replaced)**                  | Switched to MySQL via Docker Compose based on stronger personal familiarity with Docker/MySQL — improves both build speed and ability to defend setup decisions in review.                                                                                                                   |
| 9 | `spring-boot-docker-compose` auto-start integration                                      | **Accepted**                             | Verified working — `docker-compose up`, DB, and schema init all trigger automatically on application run, satisfying the "setup instructions" deliverable with a single command.                                                                                                             |

---

## 4. Independent Debugging (Engineer-Led, Not AI-Led)

Instances where the root cause was identified by the engineer, not the AI tool, demonstrating
retained ownership of correctness:

- **Null constraint failure on insert**: correctly traced to the two-phase save pattern
  (`short_code` unset at first insert) before AI confirmed/explained the fix.
- **Async/cache silently not engaging**: independently suspected the `@Component` vs
  `@Configuration` distinction as the likely cause, which AI confirmed as correct.
- **Verification over assumption**: rather than trusting that `@Async`/`@Cacheable` "should"
  work once configured, explicit log statements were added to prove behavior via thread names
  and repeated-query absence — a testing discipline applied throughout, not just accepted from
  suggestions.

---

## 5. Quality Gates Applied at Each Stage

- **Compile check** after every stage before proceeding to the next
- **Manual API verification** via Postman/curl for every new endpoint
- **Log-based behavioral proof** (not just "no errors") — e.g. confirming async via thread
  name prefixes, confirming cache via absence of repeated SQL queries
- **Exception path testing** — verified 404 (not found), 410 (expired), 409 (alias conflict),
  429 (rate limited), and 400 (validation) all return structured JSON errors, not raw stack
  traces
- **Rate limit load testing** via Postman Collection Runner (25 iterations, 0ms delay)

---

## 6. Security & Safe AI Usage Notes

- No secrets, credentials, or proprietary code were shared with the AI tool beyond this
  greenfield assignment's own source
- All AI-suggested code was reviewed and run locally before being considered "done" —
  nothing was committed without manual verification
- Design decisions with security implications (e.g. random vs sequential short codes) were
  explicitly interrogated rather than accepted on first suggestion

---

## 7. Summary

AI was used as an accelerator for implementation speed and design-rationale articulation,
not as an autonomous decision-maker. Every architectural choice — database, caching
strategy, async model, rate-limiting approach, short-code generation strategy — was stated
with a rationale, and several initial AI suggestions were explicitly rejected or edited based
on engineering judgment and hands-on debugging. This matches the assignment's core
principle: AI assists within tasks; the engineer owns execution and quality.

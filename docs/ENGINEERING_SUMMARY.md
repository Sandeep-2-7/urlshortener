# Final Engineering Summary

## Plan & Rationale

The project was executed in 10 sequential stages: requirement analysis, project setup, core
domain (shorten/redirect), analytics, reliability (exceptions, rate limiting, collision
handling), AI-usage documentation, testing, a brownfield enhancement, an ambiguous-requirement
scenario, and final documentation. Each stage was scoped, implemented, manually verified, and
only then progressed — mirroring standard engineering discipline rather than generating the
whole system at once.

Core technology choices (Spring Boot 4/Java 17, MySQL via Docker, Caffeine, `@Async`) were
made to match existing hands-on experience.

## Artifacts Produced

- Working Spring Boot prototype (shorten, redirect, analytics, trending, deactivate)
- `docker-compose.yml` + `schema.sql` — one-command reproducible setup
- Unit tests (`UrlServiceImplTest`, `AnalyticsServiceImplTest`) and integration tests
  (`UrlControllerIntegrationTest`) using H2
- `ARCHITECTURE.md` — component overview, request flows, design rationale
- `SETUP.md` — setup and run instructions
- `AI_USAGE.md` — AI-assisted engineering traceability (accepted/edited/rejected suggestions)
- `AMBIGUOUS_SCENARIO.md` — ambiguous requirement handling (trending feature)
- Brownfield enhancement (deactivate endpoint + cache-eviction fix) documented in Stage 8

## Risks, Trade-offs & Validation

| Risk/Trade-off | Mitigation/Status |
|---|---|
| Random short-code collisions | Bounded retry (5 attempts) with explicit failure over infinite loop |
| Async analytics failure | Caught and logged, never propagates to user-facing redirect |
| Stale cache on mutation (deactivate) | `@CacheEvict` added; verified via integration test |
| Rate limiter not distributed-safe | Documented limitation — acceptable for single-instance scope, noted scaling path (Redis) |
| Denormalized click count vs. live count | Accepted consistency risk for read performance; async write already logs failures |
| No auth/ownership model | Documented as out-of-scope assumption; trending/analytics are global |

## Assumptions

- Single-instance deployment (no distributed rate-limiting/caching requirement)
- No user authentication required for this assignment's scope
- "Trending" interpreted as 24-hour rolling click count (see `AMBIGUOUS_SCENARIO.md` for full
  reasoning)
- Schema evolution via plain `schema.sql` is acceptable for assignment scope (Flyway/Liquibase
  would be used in production)

## Limitations

- No pagination on recent-clicks list (hardcoded limit of 20)
- Trending computed on-demand, not precomputed/scheduled
- Rate limiting resets on app restart (in-memory, not persisted)
- No authentication/authorization layer

## Testing Approach

- **Automated unit + integration tests run during the build** (`mvn test`): service logic in
  isolation (Mockito), including deterministic testing of the non-deterministic
  collision-retry path via mocked sequential encoder returns, plus full-context integration
  tests (`MockMvc` + H2) verifying real wiring (JPA, cache, `@Transactional`, exception
  handling) end-to-end. See `E2E_TEST_CASES.md` for the full scenario-to-test mapping.
- **Manual verification (supplementary, not build-blocking)**: Postman/curl testing during
  development, including log-based proof (thread names, SQL query presence/absence) rather
  than assuming annotations worked correctly — this caught a real bug (`@Component` vs
  `@Configuration` silently disabling `@Async`/`@Cacheable`). Retained as manual checks for
  signals that don't map cleanly to a single assertion (console log inspection).
- **Load testing**: rate limiter verified via Postman Collection Runner (25 iterations) —
  kept manual since the test profile deliberately raises the rate limit to avoid interfering
  with the automated suite.
## Conclusion

This submission demonstrates engineer-led execution accelerated by AI assistance. Every major design decision — short-code strategy, caching
approach, async model, rate-limiting method — has a documented rationale and considered
alternative. Two real production-style bugs were identified and fixed during development (DB
null-constraint violation, silently-disabled `@Async`/`@Cacheable` due to annotation
misconfiguration), both diagnosed through direct engineering investigation rather than assumed
correct.

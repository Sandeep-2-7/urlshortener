# End-to-End Test Cases (Manual)

These scenarios run automatically as part of the build via `mvn test` — each one is backed
by a `@Test` method in the unit/integration test suite (`UrlServiceImplTest`,
`AnalyticsServiceImplTest`, `UrlControllerIntegrationTest`, `TrendingIntegrationTest`) and
executes against an in-memory H2 database with no Docker dependency, so it runs the same way
locally and in CI.
```bash

mvn test
```
The equivalent `curl` request is shown under each scenario for manual reproduction or
debugging against a live instance if needed — it is not a required manual step for
validating the build.
## 1. Shorten URL — Happy Path
**Automated test:** `UrlServiceImplTest.shortenUrl_generatesUniqueCode_whenNoCustomAlias`
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://www.google.com"}'
```
**Expect:** `200`, JSON with `shortUrl`, `originalUrl`, `expiresAt: null`

## 2. Shorten with Custom Alias
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://spring.io", "customAlias": "spring1"}'
```
**Expect:** `200`, `shortUrl` ends in `/spring1`

## 3. Shorten with Duplicate Custom Alias
**Automated test:** `UrlControllerIntegrationTest.shorten_returnsConflict_whenCustomAliasAlreadyTaken`
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://another.com", "customAlias": "spring1"}'
```
**Expect:** `409 Conflict`, JSON error body

## 4. Shorten with Invalid URL
**Automated test:** `UrlControllerIntegrationTest.shorten_returnsBadRequest_whenUrlInvalid`
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "not-a-url"}'
```
**Expect:** `400 Bad Request`, structured JSON error (not a stack trace)

## 5. Shorten with Expiry
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://expiring.com", "expiryInDays": 1}'
```
**Expect:** `200`, `expiresAt` populated ~24h ahead

## 6. Redirect — Happy Path
**Automated test:** `UrlControllerIntegrationTest.shortenAndRedirect_fullFlow_worksEndToEnd`
```bash
curl -i http://localhost:8080/{shortCodeFromStep1}
```
**Expect:** `302 Found`, `Location: https://www.google.com`

## 7. Redirect — Non-Existent Code
**Automated test:** `UrlControllerIntegrationTest.redirect_returnsNotFound_whenShortCodeDoesNotExist`
```bash
curl -i http://localhost:8080/doesnotexist99
```
**Expect:** `404 Not Found`, JSON error

## 8. Redirect — Verify Cache Hit (no repeated SQL)

**Manual verification only** — not part of the automated suite, since it asserts on console
log output rather than a response value. Hit step 6's URL twice, check console logs —
`SELECT ... url_mapping` should appear only on the **first** call, not the second.

## 9. Analytics — Stats After Clicks
**Automated test:** `AnalyticsServiceImplTest.recordClickAsync_incrementsClickCount_onSuccess`,
`AnalyticsServiceImplTest.getStats_returnsCorrectTotalClicks`
```bash
curl http://localhost:8080/api/urls/{shortCode}/stats
```
**Expect:** `totalClicks: 3`, `recentClicks` array populated

## 10. Analytics — Verify Async Thread
**Manual verification only** — not part of the automated suite, since it asserts on console
log/thread-name output rather than a response value. Check console log during step 9 — click
logging should show a thread name like `analytics-1`, not the Tomcat request thread
(`nio-8080-exec-*`), confirming it ran on the background executor.

## 11. Trending
**Automated test:** `TrendingIntegrationTest.trending_returnsUrlsOrderedByRecentClickCount`,
`AnalyticsServiceImplTest.getTrendingUrls_returnsUrlsOrderedByRecentClicks`
```bash
curl http://localhost:8080/api/urls/trending?limit=5
```
**Expect:** `200`, list ordered by `recentClicks` descending

## 12. Deactivate URL + Cache Eviction Check (critical brownfield regression test)
**Automated test:** `UrlControllerIntegrationTest.deactivateUrl_evictsCacheAndBlocksRedirect`
```bash
curl -i http://localhost:8080/{shortCode}                              # warm cache — expect 302
curl -X PATCH http://localhost:8080/api/urls/{shortCode}/deactivate    # expect 204
curl -i http://localhost:8080/{shortCode}                              # expect 410, NOT 302
```
**Expect:** Final call must return `410 Gone`. If it still returns `302`, cache eviction on
deactivation is broken — this is the exact bug the brownfield scenario was designed to catch.

## 13. Expired URL
**Automated test:** `UrlServiceImplTest.resolveShortCode_throwsExpired_whenPastExpiryDate`
```bash
curl -i http://localhost:8080/{expiredShortCode}
```
**Expect:** `410 Gone`

## 14. Rate Limiting
**Manual verification only** — not part of `mvn test`, since the rate limit window
(`app.rate-limit.max-requests-per-minute`) is set high in the test profile specifically to
avoid unrelated tests failing from rate limiting. Verify separately via Postman Collection
Runner: 25 iterations, 0ms delay, on `POST /api/urls`.
**Expect:** first 20 requests → `200`, remaining 5 → `429 Too Many Requests`
## 15. Full Automated Suite (run everything at once)
```bash
mvn test
```
**Expect:** all unit tests (`UrlServiceImplTest`, `AnalyticsServiceImplTest`) and integration
tests (`UrlControllerIntegrationTest`, `TrendingIntegrationTest`) pass automatically as part
of the build — no manual Postman/curl steps required for these. Runs against H2, no Docker
dependency required. Tests 8, 10, and 14 above remain manual-only checks, run separately when
needed (log inspection / load testing), since they don't map cleanly to a single assertable
`@Test`.
# End-to-End Test Cases (Manual)

Run these in order against the running app (`docker-compose up` + `mvn spring-boot:run`).

## 1. Shorten URL — Happy Path
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
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://another.com", "customAlias": "spring1"}'
```
**Expect:** `409 Conflict`, JSON error body

## 4. Shorten with Invalid URL
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
```bash
curl -i http://localhost:8080/{shortCodeFromStep1}
```
**Expect:** `302 Found`, `Location: https://www.google.com`

## 7. Redirect — Non-Existent Code
```bash
curl -i http://localhost:8080/doesnotexist99
```
**Expect:** `404 Not Found`, JSON error

## 8. Redirect — Verify Cache Hit (no repeated SQL)
Hit step 6's URL twice, check console logs — `SELECT ... url_mapping` should appear only on
the **first** call, not the second.

## 9. Analytics — Stats After Clicks
Hit redirect 3 times, wait ~1s (async write), then:
```bash
curl http://localhost:8080/api/urls/{shortCode}/stats
```
**Expect:** `totalClicks: 3`, `recentClicks` array populated

## 10. Analytics — Verify Async Thread
Check console log during step 9 — click logging should show a thread name like
`analytics-1`, not the Tomcat request thread (`nio-8080-exec-*`), confirming it ran on the
background executor.

## 11. Trending
Generate different click counts on 2+ short codes, then:
```bash
curl http://localhost:8080/api/urls/trending?limit=5
```
**Expect:** `200`, list ordered by `recentClicks` descending

## 12. Deactivate URL + Cache Eviction Check (critical brownfield regression test)
```bash
curl -i http://localhost:8080/{shortCode}                              # warm cache — expect 302
curl -X PATCH http://localhost:8080/api/urls/{shortCode}/deactivate    # expect 204
curl -i http://localhost:8080/{shortCode}                              # expect 410, NOT 302
```
**Expect:** Final call must return `410 Gone`. If it still returns `302`, cache eviction on
deactivation is broken — this is the exact bug the brownfield scenario was designed to catch.

## 13. Expired URL
Shorten with a very short expiry (or manually set `expires_at` in the DB to the past), then
redirect to it.
```bash
curl -i http://localhost:8080/{expiredShortCode}
```
**Expect:** `410 Gone`

## 14. Rate Limiting
Via Postman Collection Runner: 25 iterations, 0ms delay, on `POST /api/urls`.
**Expect:** first 20 requests → `200`, remaining 5 → `429 Too Many Requests`

## 15. Automated Test Suite
```bash
mvn test
```
**Expect:** all unit tests (`UrlServiceImplTest`, `AnalyticsServiceImplTest`) and integration
tests (`UrlControllerIntegrationTest`, `TrendingIntegrationTest`) pass. Runs against H2,
no Docker dependency required.
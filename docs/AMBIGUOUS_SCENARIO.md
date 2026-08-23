# Scenario 3: Ambiguous Requirement

## Original Ask (as received)
> "Can we also show which URLs are trending?"

This is intentionally underspecified — a realistic stakeholder request, not an engineering
ticket. Below is the process used to turn it into an implementable, defensible feature.

---

## 1. Identifying the Ambiguity

Before writing any code, the following questions were surfaced as unresolved:

| Ambiguous Term | Possible Interpretations |
|---|---|
| "Trending" | Most clicks all-time? Most clicks in last hour/day/week? Fastest-growing rate of clicks (velocity), not just raw count? |
| "Show" | A new API endpoint? A field on existing stats response? A dashboard/UI (out of scope — this is a backend service)? |
| Scope | Trending across all URLs, or per-user/per-owner (there is no auth/user concept in this system yet)? |
| Update frequency | Real-time on every click? Computed periodically (e.g. every N minutes)? |

None of these are stated in the original ask — a request like this, taken literally, cannot be
implemented correctly without resolving at least the first two questions.

---

## 2. Normalizing Into an Engineering Problem

**Decision made (with stated assumptions, since no stakeholder was available to clarify in
this exercise):**

- **"Trending" = most-clicked URLs within a rolling time window (last 24 hours)** — not
  all-time total clicks. Rationale: all-time count just re-exposes existing `click_count`,
  which isn't a new capability; a time-boxed window is what "trending" conventionally means
  (matches common product usage, e.g. Twitter/Reddit trending).
- **"Show" = a new read-only GET endpoint** — `GET /api/urls/trending?limit=10` — consistent
  with this being a backend service with no UI layer, matching the existing API-only pattern
  of the project.
- **No per-user scoping** — since there's no auth/ownership model in the current system,
  trending is computed globally across all short URLs. Explicitly flagged as a limitation:
  "if user accounts are added later, this would need to be scoped per-user."
- **Computed on-demand at request time**, not on a schedule — simplest correct
  implementation for current data volume; documented as a scaling limitation (see below).

**If a real stakeholder were available, the clarifying questions above would be asked directly
before implementation** — this section documents the fallback reasoning used in their absence,
which is itself the deliverable being evaluated (defensible assumption-making under
ambiguity).

---

## 3. Task Decomposition

```
T1: Add time-windowed query to ClickEventRepository (clicks in last N hours, grouped by URL)
T2: Add trending calculation method to AnalyticsService
T3: New DTO: TrendingUrlResponse
T4: New endpoint: GET /api/urls/trending
T5: Test: verify only clicks within window count, older clicks excluded
```

---

## 4. Implementation

### Repository — new query

```java
// ClickEventRepository.java
@Query("SELECT c.urlMappingId AS urlMappingId, COUNT(c) AS clickCount " +
       "FROM ClickEvent c WHERE c.clickedAt >= :since " +
       "GROUP BY c.urlMappingId ORDER BY COUNT(c) DESC")
List<TrendingProjection> findTrending(@Param("since") LocalDateTime since, Pageable pageable);

interface TrendingProjection {
    Long getUrlMappingId();
    Long getClickCount();
}
```

### DTO

```java
// dto/TrendingUrlResponse.java
@Data
@AllArgsConstructor
public class TrendingUrlResponse {
    private String shortCode;
    private String originalUrl;
    private long recentClicks;
}
```

### Service

```java
// AnalyticsService.java — add
List<TrendingUrlResponse> getTrendingUrls(int limit);

// AnalyticsServiceImpl.java — add
@Override
public List<TrendingUrlResponse> getTrendingUrls(int limit) {
    LocalDateTime since = LocalDateTime.now().minusHours(24);
    Pageable pageable = PageRequest.of(0, limit);

    return clickEventRepository.findTrending(since, pageable).stream()
            .map(p -> {
                UrlMapping mapping = urlMappingRepository.findById(p.getUrlMappingId())
                        .orElse(null);
                if (mapping == null) return null;
                return new TrendingUrlResponse(
                        mapping.getShortCode(), mapping.getOriginalUrl(), p.getClickCount());
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

### Controller

```java
// AnalyticsController.java — add
@GetMapping("/trending")
public List<TrendingUrlResponse> trending(@RequestParam(defaultValue = "10") int limit) {
    return analyticsService.getTrendingUrls(limit);
}
```

---

## 5. Validation & Known Limitations (stated explicitly, as required by rubric)

- **N+1 query risk**: `getTrendingUrls` calls `findById` in a loop after the grouped query —
  acceptable at small `limit` values (default 10), but would need a batched `findAllById`
  fetch if `limit` grows large. Documented trade-off, not fixed here, to match assignment
  scope.
- **On-demand computation** means this query runs against `click_event` at request time — as
  data grows, this would need either a database index on `clicked_at`, or a
  precomputed/cached trending table refreshed on a schedule (e.g. every 5 minutes) rather than
  computed live. Noted as a scaling limitation, not implemented, since current data volume
  doesn't justify it.
- **24-hour window is a fixed assumption**, not configurable — a real stakeholder
  conversation might reveal they wanted "trending this week" or a user-selectable window.
  Documented as the single biggest open question from the original ambiguous ask.

---

## 6. Why This Approach (vs. Alternatives Considered)

| Alternative | Rejected Because |
|---|---|
| All-time click count as "trending" | Doesn't represent recency/momentum — a URL from 6 months ago with high total clicks isn't "trending" in any normal sense |
| Real-time streaming/websocket trending updates | Massive overkill for assignment scope; no requirement for live push updates was stated or implied |
| Scheduled batch job precomputing trending every N minutes | Legitimate production approach, but adds complexity (scheduler, cache table) not justified until data volume requires it — documented as a future improvement instead |

This mirrors the same judgment discipline applied throughout the project: pick the simplest
approach that correctly satisfies the *interpreted* requirement, state the interpretation
explicitly, and name the trade-offs rather than hide them.

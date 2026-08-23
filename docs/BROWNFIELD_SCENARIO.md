# Scenario 2: Brownfield

## Requirement (as given)
> Enhance the existing URL shortener with the ability to deactivate/disable a short URL
> without deleting it.

This is a brownfield change — made against the already-built, already-running system from
the greenfield stage (entities, caching, and analytics already in place), not a fresh build.

---

## 1. Codebase Reasoning (Impact Analysis)

Before writing any code, the existing system was analyzed to identify what this change would
touch:

| Component | Impact | Why |
|---|---|---|
| `UrlMapping` entity | Add `active` boolean column | Need a soft-disable flag; avoids hard delete so click-history/analytics is preserved |
| `UrlMappingRepository` | No change | `findByShortCode` already fetches the full entity |
| `UrlServiceImpl.resolveShortCode()` | Must check `active` flag before allowing redirect | Redirect must reject disabled links |
| **Caffeine cache (`urlCache`)** | **Must add `@CacheEvict`** | This is the critical brownfield risk — `resolveShortCode` was already cached from the greenfield build; without eviction, a deactivated link stays reachable for up to the cache TTL (10 min) after deactivation |
| New service method: `deactivateUrl()` | New | The actual toggle action |
| Controller | New `PATCH` endpoint | Expose the action |
| Schema | `ALTER TABLE` migration needed | Existing rows need a default value for the new column |

This impact table is the deliverable itself — it demonstrates identifying affected
modules/data flows in an existing system before modifying it, rather than just writing code
directly.

---

## 2. Task Decomposition

```
T1: Schema migration — add `active` column with default TRUE       [no deps]
T2: Update UrlMapping entity + @PrePersist default                  [depends: T1]
T3: Update resolveShortCode() to reject inactive URLs                [depends: T2]
T4: Add deactivateUrl() service method with @CacheEvict              [depends: T2]
T5: New UrlDeactivatedException + exception handler mapping           [depends: T3]
T6: New PATCH endpoint on UrlController                                [depends: T4]
T7: Integration test proving cache eviction actually works             [depends: T4, T6]
```

---

## 3. Execution

### Schema migration
```sql
ALTER TABLE url_mapping ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
```

### Entity change
```java
@Column(name = "active", nullable = false)
private boolean active;

@PrePersist
public void prePersist() {
    this.createdAt = LocalDateTime.now();
    if (this.clickCount == null) this.clickCount = 0L;
    this.active = true;
}
```

### Service layer — the two critical changes
```java
@Override
@Cacheable(value = "urlCache", key = "#shortCode")
public UrlMapping resolveShortCode(String shortCode) {
    UrlMapping saved = urlMappingRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));

    if (!saved.isActive()) {
        throw new UrlDeactivatedException(shortCode);
    }
    if (saved.getExpiresAt() != null && saved.getExpiresAt().isBefore(LocalDateTime.now())) {
        throw new UrlExpiredException(shortCode);
    }
    return saved;
}

@Override
@Transactional
@CacheEvict(value = "urlCache", key = "#shortCode")
public void deactivateUrl(String shortCode) {
    UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));
    mapping.setActive(false);
    urlMappingRepository.save(mapping);
}
```

The `@CacheEvict` line is the actual brownfield lesson here: it would be easy to implement
this feature, test it once manually (DB updates correctly), and ship it — while missing that
anyone who already triggered a cache hit on that short code keeps getting redirected for up
to 10 minutes after "deactivation." This is precisely the class of bug that only surfaces
when modifying a system that already has caching, not when building fresh.

### Controller
```java
@PatchMapping("/api/urls/{shortCode}/deactivate")
public ResponseEntity<Void> deactivate(@PathVariable String shortCode) {
    urlService.deactivateUrl(shortCode);
    return ResponseEntity.noContent().build();
}
```

---

## 4. Validation

**Manual regression test (proves the cache-eviction fix, not just the feature):**
```bash
curl -i http://localhost:8080/{shortCode}                              # warm cache -> 302
curl -X PATCH http://localhost:8080/api/urls/{shortCode}/deactivate    # -> 204
curl -i http://localhost:8080/{shortCode}                              # must be 410, not 302
```

**Automated integration test:**
```java
@Test
void deactivateUrl_evictsCacheAndBlocksRedirect() throws Exception {
    // ... create URL, warm cache with one redirect, deactivate, then assert
    // the next redirect returns 410 Gone instead of a stale cached 302
}
```
(Full test in `TrendingTests.java` / integration test suite from Stage 7-8.)

---

## 5. Risk & Trade-off Notes

| Risk | Mitigation |
|---|---|
| Cache staleness after mutation | `@CacheEvict` added; verified via regression test above |
| Hard delete would lose analytics history | Chose soft-deactivation (`active` flag) instead — reversible, preserves `click_event` history |
| Schema change on a live table | Used `ALTER TABLE ... DEFAULT TRUE` so existing rows remain valid without a backfill script |

**Broader lesson applied:** any future mutation added to a field that participates in a
`@Cacheable` read path must be checked for a matching `@CacheEvict` (or cache key
invalidation strategy) — this is now a standing review checklist item for this codebase.

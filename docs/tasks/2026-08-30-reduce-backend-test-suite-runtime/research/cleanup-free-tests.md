# Cleanup-Free Tests via a Fresh User per Test — Research

Can `@AfterEach` cleanup be removed entirely by giving every test its own user, the way the
`dev` profile hands out an identity per bearer token? This is the feasibility analysis, with
everything measured on this machine at `0bca31a`. It is analysis, not an implementation plan.

Companion to `backend-test-suite-runtime.md`, which measured where the suite's 85 s goes.

## Summary

**Yes, almost entirely — and it works today.** The backend has no user table: an identity is
nothing but the `email` claim, so "a new user" costs one string. The suite does not even need a new
decoder — running tests on `@ActiveProfiles({"dev", "test"})` reuses the production `DevAuthConfig`
bypass, with a test-owned `application-test.yml` overriding the dev settings tests should not
inherit. Measured: **284 tests green, no production code touched, still 8 Spring contexts**. One
suite was then converted to a fresh user per test with its `@AfterEach` deleted outright, and it
stayed green both alone and in the full suite.

But three things temper it:

1. **The prize is small on its own.** All teardown across the suite costs **6.5 s** of ~85 s,
   measured directly. Most of that is one nested class paying the production-S3 tax on recipe
   deletion. The real value of per-test users is that they are the prerequisite for the shared
   container (−21 s) and for parallelism, not the 6.5 s.
2. **A residue survives.** Three tests mutate the `limit_config` *default* row (`subject IS NULL`),
   which no per-subject scheme isolates, and one assertion counts `limit_usage` rows globally.
3. **It deletes a real detector.** Today's `assertThat(usedFor(SUBJECT)).isZero()` in teardown is
   the suite's last line of defence against a missed release. Fresh users make that assertion
   trivially true, so it must be re-expressed inside the tests that own release.

**Unrelated to the question but far larger:** the test `RestClient` silently **retries every 429
after a one-second sleep**. The suite asserts 24 refusals, so it sleeps ~24 s per run. Disabling
retries took the JUnit-measured time from **40.8 s → 15.1 s** with 284 tests green — a bigger win
than everything in the companion document combined, from one line.

## Key findings

- **There is no user in this system.** No users table, no registration, no provisioning. Every
  controller does `jwt.getClaimAsString("email")` and passes the string down; `limits` treats the
  subject as opaque (`docs/ADRs/0006`). A fresh user is a fresh string — nothing to create, nothing
  to clean up.
- **`DevAuthConfig` is the decoder, not just the pattern.** `token -> Jwt` with
  `token + "@local.test"` as the email is exactly what the tests need, and activating the `dev`
  profile alongside a `test` profile lets the suite use it directly — one bypass in the codebase
  instead of two, and the one developers already exercise locally.
- **Measured teardown cost: 6 483 ms**, over 8 `@AfterEach` methods and 284 tests.
- **Half of that is one class**: `RecipeIntegrationTest$LimitsEnforced`, 3 236 ms for 14 tests —
  ~230 ms per test, which is the S3 `ListObjectsV2` tax on `DELETE /recipes/{id}` documented in the
  companion research. Fixing S3 removes most of it *without* touching cleanup.
- **Three of the four big web suites already have no outer `@AfterEach`** — the pre-existing defect
  the companion document identified. Per-test users fix that defect rather than working around it.
- **What per-test users do not isolate**: the `limit_config` default row (3 tests set it and restore
  it in a `finally`), one global `COUNT(*)` over `limit_usage` for `SHOPPING_LIST_ITEM`, and
  `LimitsIntegrationTest`'s `TEST_LIMIT_*` rows — that suite is subject-agnostic and wants unique
  *resource* names instead.
- **Migration size**: 136 references to the three `AUTH_TOKEN*` constants and 129 hard-coded email
  literals across ~7 000 lines of test code, all of which become `emailOf(user)` expressions in the
  fresh-user migration anyway. Mechanical, but wide.

## Details

### 1. Why a fresh user is free here

`SecurityConfig` is a plain OAuth2 resource server; identity enters through the `email` claim and
never touches a table. `RecipeController` is representative:

```java
public List<RecipeListDto> getAllRecipes(..., @AuthenticationPrincipal Jwt jwt) {
    String userEmail = jwt.getClaimAsString("email");
```

Ownership lives in the permission tables (`recipe_permission`, `shopping_list_permission`, …) keyed
by that email, and `limit_usage` is keyed by `(resource, subject)` where the subject is the same
string. Two tests using different emails share no row anywhere. The migrations create no `users`
table — `V1` created `user_recipes`, which `V6` renamed to `recipe_permission`; that is the whole of
"users" in the schema.

This is what makes the idea cheap. In a system with registration, "a new user per test" costs a
signup round trip per test; here it costs `UUID.randomUUID()`.

### 2. Reuse `DevAuthConfig` via a `test` profile (measured)

`TestSecurityConfiguration` currently mocks `JwtDecoder` with Mockito and stubs exactly three
tokens; any fourth token returns `null`. That is the only thing standing in the way — and the
project already owns a decoder that accepts any token.

The gate on `DevAuthConfig` is `@Profile("dev")`, and the tests need it *without* the rest of
`application-dev.yml` (`logging.level.xyz.stasiak: DEBUG`, `recipai.limits.enabled: false`,
a 60-minute presigned-URL expiry). Both are satisfied by activating two profiles and letting the
second override the first — Spring Boot applies a
[last-wins strategy across active profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html):

```java
@ActiveProfiles({"dev", "test"})          // dev contributes DevAuthConfig; test overrides its settings
@Import(TestcontainersConfiguration.class) // TestSecurityConfiguration no longer imported
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "recipai.limits.enabled=false")
```

with `src/test/resources/application-test.yml` — test classpath only, so it can never reach a
deployed jar:

```yaml
logging:
  level:
    xyz.stasiak: INFO
recipai:
  s3:
    presigned-url-expiration-minutes: 10
```

`TestSecurityConfiguration` stops being a `@TestConfiguration` and becomes a constants holder that
mirrors the dev rule:

```java
public static final String AUTH_TOKEN = "user";          // was "test-jwt-token"
public static final String AUTH_TOKEN_USER_1 = "user1";
public static final String AUTH_TOKEN_USER_2 = "user2";

public static String freshToken() { return "u" + UUID.randomUUID().toString().replace("-", ""); }
public static String emailOf(String token) { return token + "@local.test"; }
```

**Measured: 284 tests, 0 failures. No file under `src/main` changed. Still 8 Spring contexts** — the
annotation is applied uniformly, so it adds nothing to the cache key. The `application-test.yml`
override was verified to win: `application-dev.yml` sets `xyz.stasiak` to `DEBUG` and the run
produced zero such lines. `DevAuthConfig`'s startup `WARN` fires once per context (8 lines), which
is a fair price for an announced bypass.

The one substantive cost: dev auth hardcodes `@local.test`, so the 129 `@example.com` literals must
be renamed. Since the fresh-user migration turns nearly all of them into `emailOf(user2)`
expressions anyway, the two changes overlap almost completely.

**The alternative considered and rejected:** keeping a test-owned lambda `JwtDecoder` in
`TestSecurityConfiguration` with an `@example.com` domain. It is also drop-in (measured: 284 green,
zero other edits) and avoids the literal rename, but it leaves two "accept any token" bypasses in
the codebase where only one is exercised by tests. Reuse wins; the literal rename is a one-time sed.

A third option — widening the gate to `@Profile({"dev", "test"})` and activating `test` alone — was
not pursued. It edits production code to add a second magic string that disarms authentication, for
no benefit over the two-profile form.

### 3. One suite, fully cleanup-free (measured)

`RecipesCollectionIntegrationTest` (21 tests, sharing, quotas, recompute) was converted:

```java
private String owner, user1, user2;

@BeforeEach
void freshUsers() {
    owner = TestSecurityConfiguration.freshToken();
    user1 = TestSecurityConfiguration.freshToken();
    user2 = TestSecurityConfiguration.freshToken();
}
```

`restClient()` uses `owner`; every `"user2@example.com"` became `emailOf(user2)`; the nested
`LimitsEnforced` `SUBJECT` constant became an instance field set from `owner` in its `@BeforeEach`
(JUnit runs the outer `@BeforeEach` first, so `owner` is already assigned); the ghost subject in
the recompute test became a fresh token; **and the entire 30-line `@AfterEach` was deleted.**

- Run alone: 21 tests, 0 failures.
- Run inside the full suite: 284 tests, 0 failures — and note this suite shares its Spring context
  and database with `RecipeIntegrationTest`, `ShoppingListIntegrationTest` and
  `MealPlanIntegrationTest`, so it was genuinely exposed to their leftovers and they to its.

Nothing in the suite needed a semantic change. The assertions were already written as `contains(…)`
rather than `containsExactly(…)` — a shape adopted to tolerate leakage ("including those created in
other tests", `RecipeIntegrationTest:363`). With per-test users those could be tightened to exact
assertions, which is a correctness gain, not a requirement.

### 4. What a fresh user does not isolate

Four kinds of state remain global. Only the first is a genuine blocker for "no teardown at all".

**a. The `limit_config` default row.** Three tests — one each in `RecipeIntegrationTest`,
`RecipesCollectionIntegrationTest`, `ShoppingListIntegrationTest`, all named
`shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow` — flip the shipped default
(`subject IS NULL`) from `STOCK` to `FLOW` for the whole database and restore it in a `finally`.
That restore is not `@AfterEach` and must stay. It is also the one thing that would break under
parallel execution.

**b. A global row count.** `ShoppingListIntegrationTest` teardown asserts
`SELECT COUNT(*) FROM limit_usage WHERE resource = 'SHOPPING_LIST_ITEM'` is zero — a
"no orphaned item usage anywhere" check. With per-test users it must be narrowed to the list UUIDs
that test created. That is a rewrite, not a deletion.

**c. `LimitsIntegrationTest`.** It exercises the facade directly against synthetic `TEST_LIMIT_*`
resources and hand-picked subjects; there is no HTTP caller to give a fresh identity to. Its lever
is a unique **resource** name per test rather than a unique subject. Its teardown costs 24 ms, so
there is no reason to touch it for speed.

**d. Unbounded growth within a run.** Nothing is ever deleted, so rows accumulate for the whole
JVM. At 284 tests creating a handful of rows each this is irrelevant to PostgreSQL, but two things
scale with it: `RecomputeMigration.run` rewrites `limit_usage` for *every* subject each time it is
called (17 call sites), and any `SELECT` without a subject predicate. Both are cheap today and
neither is a correctness problem — recompute rebuilds from the permission tables, which stay
truthful for abandoned users.

### 5. What is lost, and how to keep it

`docs/backend/standards/integration-tests.md` is explicit that API-driven teardown is not
housekeeping — it is the assertion:

> Ending teardown with an assertion that the subject's usage is back to zero attributes the failure
> to the test that broke release rather than to the next one.

With a fresh subject per test, `usedFor(SUBJECT) == 0` becomes true whether or not release fired,
so the detector evaporates. It has to move into the tests that own the behaviour — several already
carry it inline (`shouldTrackUsageAcrossCreateAndDelete`,
`shouldAdmitNextCreateAndDropBalanceAfterDelete`, `shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare`).
The honest framing: per-test users replace a *blanket* release check that runs after all 64
`LimitsEnforced` tests with *explicit* checks in the ~6 tests that actually delete something. That
is a narrower net. Whether the extra coverage was worth its 6.5 s is the user's call, and it is a
standards decision, not a technical one.

An alternative that keeps both: fresh users **and** a teardown reduced to the single assertion
(no HTTP listing, no deletes). That costs one facade call per test instead of 3 listings and N
deletes, and keeps the detector intact. It is the option this research would recommend.

### 6. The 429 retry — a much larger, unrelated finding

While spiking the above, the converted suite ran 17.1 s alone but 13.2 s after one unrelated change.
The logs explain it:

```
o.a.h.c.h.i.c.HttpRequestRetryExec : ex-0000000042 http://localhost:37285 responded with status 429;
                                     request will be automatically re-executed in 1 SECONDS (exec count 2)
```

`httpclient5` is on the test classpath (5.6.1, transitively), and Spring's `RestClient` picks
Apache first when auto-detecting a request factory. Apache's `DefaultHttpRequestRetryStrategy`
defaults to **1 retry after a 1-second interval, and 429 is on its retriable list**. Every test that
asserts a refusal therefore sleeps a second and makes the server refuse twice — the `LimitService`
"Limit exceeded" warning appears twice per assertion in the baseline log.

The suite makes 24 such assertions. Disabling retries in the six test suites' `RestClient` builders:

```java
RestClient.builder()
    .requestFactory(new HttpComponentsClientHttpRequestFactory(
            HttpClients.custom().disableAutomaticRetries().build()))
```

| Measurement (JUnit-instrumented) | baseline | retries off |
|---|---|---|
| `@BeforeEach` | 240 ms | 184 ms |
| test bodies | 34 044 ms | **9 030 ms** |
| `@AfterEach` | 6 483 ms | 5 849 ms |
| **total** | **40 767 ms** | **15 063 ms** |
| Tests | 284 green | 284 green |

Per class, the collapse is exactly where the refusals are: `ShoppingListIntegrationTest$LimitsEnforced`
10 064 → 852 ms, `RecipeIntegrationTest$LimitsEnforced` 5 009 → 792 ms,
`ExtractionIntegrationTest` 5 986 → 776 ms, `MealPlanIntegrationTest$LimitsEnforced` 3 419 → 401 ms.

Wall clock moved 82 s → 53 s on that run, but wall clock on this machine swings ±10 s with container
scheduling (a later run of strictly less work took 63 s), so the JUnit-instrumented **−25.7 s** is
the number to trust. This affects test clients only; production and the Flutter app are untouched.

This is worth doing first, before anything in this document or the companion.

### 7. How it composes

The changes stack, and per-test users are the enabler for the two large ones:

| Change | Measured / expected | Status |
|---|---|---|
| Disable 429 retry in test clients | **−25.7 s** | measured, 284 green |
| `@ActiveProfiles({"dev", "test"})` for auth | 0 s | measured, 284 green — prerequisite |
| Fresh user per test, teardown removed | **−6.5 s** (−5.8 s after the retry fix) | proven on 1 of 8 suites, on top of the row above |
| Share one PostgreSQL container | −21 s (companion doc) | **blocked on the line above** |
| Stop calling production S3 | −4 s, and ~half of remaining teardown | independent |
| Parallel execution | unmeasured, potentially large | needs fresh users **and** a fix for §4a |

The companion research found that sharing a container fails today with three errors, all of them
accumulated `limit_usage` debt becoming visible ("`used = 26` against a quota of 2"). Fresh users
dissolve that class of failure at the root: there is no shared subject to accumulate against. That,
not the 6.5 s, is the argument for doing this.

For parallelism, §4a is the hard edge. Two threads cannot both flip the global default row. Those
three tests would need `@ResourceLock` or a rewrite to use a subject override instead of a default.

## Open questions

- **Keep a one-line teardown, or none at all?** §5 argues for keeping
  `assertThat(usedFor(subject)).isZero()` and deleting only the listing-and-deleting. That preserves
  the missed-release detector for ~1 ms per test. "Completely remove" is achievable, but it trades a
  detector for ~5 ms per test.
- **Does the standards document change?** `docs/backend/standards/integration-tests.md` currently
  mandates `@AfterEach` and API-driven teardown. Both rules would be replaced by "give each test its
  own subject". `configuration-profiles.md` would also need to record that `dev` is activated by the
  test suite, not only by `recipai.sh` — the bypass warning it documents now fires 8 times per test
  run. Both are team decisions.
- **Is the test suite depending on `DevAuthConfig` a feature or a coupling?** It means a change to
  the local-dev bypass breaks the build, which is good, and that the suite never exercises real JWT
  validation, which was already true under the Mockito decoder. Worth naming explicitly before
  committing to it.
- **All at once or suite by suite?** The profile change is safe alone. Each suite can then convert
  independently — but the shared-container payoff only arrives when the last of the four web suites
  is done.
- **Should `freshToken()` be readable?** `u3f9c…@local.test` in a log or a failure message is
  opaque. `"recipe-create-1"`-style names derived from the test name via `TestInfo` would debug
  better at the cost of a uniqueness guarantee across reruns of an uncleaned database.
- **Does anything upload to S3?** No test currently uploads an image. If one ever does, no-teardown
  means the object is never deleted — and per the companion research, the bucket is the production
  one. Mocking `S3Client` should land before, not after, this change.

## Sources

Measurements are from this repository at `0bca31a` (12 cores, 16 GB, Docker native, JDK 26, Spring
Boot 4.1.0). Every experiment was reverted; the working tree is unchanged.

- `backend-test-suite-runtime.md` (this directory) — the baseline profile, the eight-container
  finding, the production-S3 call in recipe deletion, and the three failures that block a shared
  container.
- `docs/backend/standards/integration-tests.md` — the current teardown mandate and the rationale for
  API-driven cleanup that this proposal would replace.
- `docs/project/local-development.md` and `config/security/DevAuthConfig.java` — the token-to-identity
  bypass this borrows, including why the email suffix exists (RFC 6750 forbids `@` in a bearer token).
- `docs/backend/modules/limits/module.md` and `docs/ADRs/0006-shared-limits-module.md` — the opaque
  subject, the config-subject vs. usage-subject split, and the override-then-default resolution that
  makes a fresh subject start at a clean quota.
- [DefaultHttpRequestRetryStrategy — Apache HttpClient 5.6 javadoc](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/impl/DefaultHttpRequestRetryStrategy.html) — the default constructor's 1 retry / 1-second interval, and 429 and 503 as its retriable status codes.
- [DefaultHttpRequestRetryStrategy source — apache/httpcomponents-client](https://github.com/apache/httpcomponents-client/blob/master/httpclient5/src/main/java/org/apache/hc/client5/http/impl/DefaultHttpRequestRetryStrategy.java) — confirms the defaults in code.
- [Calling REST Services — Spring Boot reference](https://docs.spring.io/spring-boot/reference/io/rest-client.html) — the request-factory auto-detection order that puts Apache HttpClient ahead of the JDK client whenever it is on the classpath.
- [REST Clients — Spring Framework reference](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html) — `RestClient.builder().requestFactory(…)`, the override used to disable retries.
- [Profiles — Spring Boot reference](https://docs.spring.io/spring-boot/reference/features/profiles.html) — the last-wins ordering across active profiles that lets `application-test.yml` override `application-dev.yml` while `DevAuthConfig` still loads.
- [Context Caching — Spring Framework reference](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html) — active profiles are part of the context cache key, which is why `@ActiveProfiles` must be applied uniformly (verified: still 8 contexts).
- [Isolating database data in integration tests — Jimmy Bogard](https://lostechies.com/jimmybogard/2012/10/18/isolating-database-data-in-integration-tests/) — the general argument that per-test unique data beats teardown for isolation.
- [Keeping your integration tests isolated — dontpanic.42.nl](https://dontpanic.42.nl/2013/05/keeping-your-integration-tests-isolated.html) — the same trade-off framed as "each test acts as if it is the only test".
- [Parallel Test Execution for JUnit 5 — Baeldung](https://www.baeldung.com/junit-5-parallel-tests) — the `junit.jupiter.execution.parallel.*` properties and resource synchronisation that §7's last row would need.
- [Pragmatic test parallelization with JUnit 5 — mikemybytes](https://mikemybytes.com/2021/11/24/pragmatic-test-parallelization-with-junit5/) — argues for parallelising the isolated tests first and leaving shared-resource tests alone, which matches the §4a constraint.
- [Parallel Test Execution with Testcontainers — prgrmmng.com](https://prgrmmng.com/parallel-test-execution-with-testcontainers) — data-level isolation is the application's responsibility, not the container's; the container can be shared once the data is not.

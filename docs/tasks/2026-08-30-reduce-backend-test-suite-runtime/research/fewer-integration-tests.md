# Would Fewer Integration Tests Make the Suite Faster? — Research

Three proposals were put forward: push most cases down to a data slice, push most
cases down to hand-stubbed in-memory unit tests, or a hybrid that keeps Postgres
only for complicated reads. This measures what each would actually buy on this
codebase. It is analysis, not an implementation plan.

Companion to `backend-test-suite-runtime.md` (where the 85 s goes) and
`cleanup-free-tests.md` (per-test users, and the 429-retry finding). Everything
below was measured on this machine at `0bca31a` (12 cores, 16 GB, Docker native,
JDK 26, Spring Boot 4.1.0). Every experiment was reverted; the working tree is
unchanged.

## Summary

**No — not meaningfully, and one of the three would make the suite slower.** The
suite's cost is not per-test, it is per-*context* and per-*container*, and three
fixes that touch no test count at all take it from **40.8 s of JUnit time to
23.0 s** (wall 85 s → 56 s). Against that floor the per-test saving from
downgrading a test is 10–110 ms, and only about **6 % of the 284 tests can be
downgraded without deleting the assertion** — the other 94 % assert on something
the database computes: a permission join, an `ORDER BY`, a `@Version` bump, or the
`ON CONFLICT … WHERE used < :max` upsert that *is* the quota rule.

The decisive measurement is that **once one PostgreSQL container is shared, a
`@WebMvcTest` context costs 0.79 s and a full `@SpringBootTest` context costs
0.95 s.** The received wisdom that slices are 5× cheaper is true only while every
context boots its own container. Splitting a suite into a web slice plus a data
slice costs *more* context time than leaving it whole.

| Option | Net effect, measured | Verdict |
|---|---|---|
| 1 — web slice for a few, data slice for the rest | **−0.4 s to +4 s** (worse if one slice per controller) | no |
| 2 — Spring for a few, hand-stubbed unit tests for the rest | ~**−3 s** for ~1 000 lines of stub code, and the quota rule stops being tested | no |
| 3 — Postgres for complicated reads, stubs for the rest | same as 2, minus the part that was already free | no |
| (the actual lever) — shared container + no 429 retry + mock S3 | **−17.8 s JUnit, −29 s wall** | do this |

There is a real ~20-test residue that genuinely belongs outside the integration
suite, and it is worth extracting — just not for the runtime.

## The cost model, measured

Everything hinges on which costs are per-run, per-context, per-container and
per-test. All four were measured separately.

### Fixed, unavoidable per run

| Item | Cost |
|---|---|
| `./mvnw clean test-compile` | 5.6 s |
| A run of only the 16 existing pure unit tests (JVM + Surefire, no Spring) | 2.4 s wall, 143 ms of test time |
| First Spring context in the JVM (absorbs class loading + first container) | 7.5 – 15.4 s |

The first context is expensive no matter what it is. `ExtractionIntegrationTest`
pays 13–15 s purely for being alphabetically first; the same context costs ~1 s
when it is not.

### Per context — with and without a shared container

Measured in a warm JVM, as the 9th–10th context of a full run:

| Context type | Own container per context | **One shared container** |
|---|---|---|
| Full `@SpringBootTest` (RANDOM_PORT) | 4.1 – 9.1 s | **0.93 – 1.90 s** (median 0.96 s) |
| `@DataJpaTest` + `@AutoConfigureTestDatabase(NONE)` | 3.24 s | **0.53 s** |
| `@WebMvcTest(OneController.class)` (no DB at all) | 0.84 s | **0.79 s** |

Read the right-hand column carefully. **A web slice that touches no database is
not cheaper than a full application context that does.** The container was the
expense; Spring was never the expense. The `@WebMvcTest` figure barely moves
between columns because it never had a container — which is exactly why it looks
like a 5× win in the left column and no win at all in the right.

This is the single fact that decides options 1 and 3. The
[widely repeated figure](https://medium.com/@AlexanderObregon/a-guide-to-efficient-testing-in-spring-boot-with-datajpatest-and-webmvctest-d8d1eaf2ab95)
— "`@WebMvcTest` starts in 2–3 s versus 10–15 s for `@SpringBootTest`" — is a
measurement of container boots, not of slice economy.

### Per test

Measured at steady state (40+ repetitions of the same shape in one class), after
the three fixes below:

| Test shape | Median | Mean | One-time cost |
|---|---|---|---|
| In-memory unit test, hand-written stub repositories | **2 ms** | 5 ms | 1.1 s first test (Mockito + AssertJ class loading) |
| `@WebMvcTest` + `MockMvcTester`, service mocked | **8 ms** | 11 ms | 0.79 s context |
| Full HTTP integration test (`RestClient` → Tomcat → Postgres) | **19 – 114 ms** by suite | 21 – 145 ms | shares an existing context |

Per-suite medians for the real integration tests: `LimitsIntegrationTest` 19 ms,
`RecipesCollectionIntegrationTest` 38 ms, `ShoppingListIntegrationTest` 39 ms,
`RecipeIntegrationTest` 64 ms, `MealPlanIntegrationTest` 114 ms. The spread
tracks how many HTTP calls a test makes, not whether it is an integration test.

So the honest per-test saving from a downgrade is **~11 ms** (integration → web
slice) or **~37 ms** (integration → in-memory unit test) on a typical test.

### The floor the options are competing against

Applying the three fixes the companion documents already identified — share one
container, disable the Apache HttpClient 429 retry in the test `RestClient`s, and
supply a `@Primary` mock `S3Client` — and changing no test:

| Stage | JUnit-measured total | Wall clock |
|---|---|---|
| Baseline (companion research) | 40.8 s | ~85 s |
| + shared container + retry disabled | 49.9 s * | 1:23 |
| **+ mock `S3Client`** | **23.0 s** | **56 s** (`clean`) |

\* higher than baseline only because that run still carried the live-S3 tax and
89 throwaway spike tests; the S3 row is the clean comparison.

The per-class collapse is where it was predicted to be:
`RecipeIntegrationTest$LimitsEnforced` went **15.9 s → 1.93 s**, and every
`LimitsEnforced` class landed in the 1.2–3.0 s range. Of the remaining 56 s of
wall clock, roughly 5.6 s is compile, 13–15 s is the first context, ~6 s is the
other seven contexts, 23 s is test bodies and hooks, and the rest is Maven and
JVM overhead.

**Everything below is competing for a slice of that 23 s** — and only for the
portion of it that a downgraded test would still cover.

> Reproduced with 3 pre-existing failures, the known `limit_usage` cleanup debt
> the companion research documents. They are unrelated to timing.

## Option 1 — web slice for a few cases, data slice for the rest

### What it would cost before it saves anything

Spring Boot 4 [split the test slices out of `spring-boot-test-autoconfigure`](https://spring.io/blog/2025/10/28/modularizing-spring-boot/)
into per-technology modules, and this project's `pom.xml` has neither. `@WebMvcTest`
and `@DataJpaTest` do not compile today; they need `spring-boot-starter-webmvc-test`
and `spring-boot-starter-data-jpa-test` added in test scope. Their packages also
moved — `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.

`@WebMvcTest` then fails to start against this codebase:

```
Error creating bean with name 'jwtSecurityFilterChain' … : No qualifying bean of
type 'org.springframework.security.config.annotation.web.builders.HttpSecurity'
```

`SecurityConfig` carries `@EnableWebSecurity` and is excluded by the slice's type
filter, while the OAuth2 resource-server auto-configuration is not — so the slice
needs either a test-only duplicate of the security chain or `SecurityConfig` made
public and imported. The spike used a duplicate; it works, and it is a second
security configuration to keep in step with the real one.

`@DataJpaTest` works cleanly (`@AutoConfigureTestDatabase(replace = NONE)` plus the
existing `TestcontainersConfiguration`), and Flyway runs, so the real schema is
present. That part is sound.

### The arithmetic

Controllers and services here are package-private, so a `@WebMvcTest` lives in the
module's own package — fine. The question is how many contexts it creates.
`@WebMvcTest(X.class)` with different `X` produces a
[different merged context configuration and therefore a different cached context](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html):

- one slice per controller (6 controllers): **+4.7 s** of context time
- one un-parameterised `@WebMvcTest` shared by all controller tests: **+0.8 s**
- plus a `@DataJpaTest` context: **+0.5 s**

Against that, moving a test from the integration suite to a web slice saves 11 ms.
**Break-even is ~72 tests for the one-shared-context shape, and ~470 tests for the
one-slice-per-controller shape.** The suite has 284 tests in total.

### And only 15 of them can move

The 129 plain (non-`LimitsEnforced`) tests in the four web suites, classified by
what their assertion actually depends on:

| Count | Share | What the assertion depends on |
|---|---|---|
| 50 | 38 % | permission / ownership rows (`*_permission` tables, JPQL joins) |
| 33 | 25 % | a query or projection result (`ORDER BY`, calendar projection, unassigned filter) |
| 31 | 24 % | a CRUD round-trip through the database |
| **15** | **11 %** | **request validation — the web layer alone** |

Only the last row survives a `@WebMvcTest`, and even that overstates it: five of
the fifteen are *service*-layer rules, not Bean Validation —
`MealPlanService.validateEntry` throws `InvalidMealPlanEntryException` and
`MealPlanCalendarService` throws `InvalidDateRangeException`. In a slice with a
mocked `MealPlanService` those tests would assert that the exception handler maps
an exception the test itself told the mock to throw.

That leaves **~10 genuinely web-layer tests** across the whole suite. Moving them
saves 0.1 s and costs 0.8 s of context.

The other 47 `403` and 38 `404` assertions in these suites look like status-code
tests but are not: they are the *outcome* of a permission row lookup. In a slice
they become `when(service.findById(…)).thenThrow(AccessDeniedException.class)` —
a test that the stub throws what it was told to.

**Verdict: no. Between −0.4 s and +4 s depending on shape, and it converts the
suite's permission coverage into mock choreography.**

## Option 2 — Spring for a few cases, hand-stubbed in-memory unit tests for the rest

This was built, not estimated: an in-memory base plus stubs for the three
shopping-list repositories, and a `ShoppingListService` unit test that passed.

### What it takes to write

| File | Lines |
|---|---|
| `InMemoryJpaRepository<T, ID>` — the 36 methods the compiler demands of `JpaRepository` | 173 |
| Stubs for `ShoppingListRepository`, `ShoppingListItemRepository`, `ShoppingListPermissionRepository` | 123 |
| **Infrastructure to unit-test one module's service** | **296** |

Six modules with ~14 repositories puts this near **1 000 lines of test-only code**,
before the tests themselves. The base class absorbs ~30 of the 36 methods, so the
marginal cost per additional repository is real but modest — perhaps 40 lines.

### What the stubs had to reimplement

Three things, in the one module that was tried:

```java
// hand-written stand-in for the JPQL join in findAllByUserEmail
return permissions.store.values().stream()
        .filter(p -> p.getId().email().equals(email))
        .map(p -> store.get(p.getId().shoppingListId()))
        .sorted(Comparator.comparing(ShoppingList::getCreatedAt))
        .toList();

// hand-written stand-in for Hibernate's @Version increment
entity.setVersion(entity.getVersion() == null ? 0L : entity.getVersion() + 1);
```

Each is a place where the test can agree with the stub and disagree with
production. `shouldIsolateShoppingListsBetweenUsers` would then be asserting that
the *stub's* stream pipeline filters by email — the production JPQL could be
deleted and the test would still pass. This is the
[standard objection to a fake you own but do not verify](https://abseil.io/resources/swe-book/html/ch13.html):
a fake is only useful if its behaviour matches the real implementation, which is
established by running the same suite against both — and running it against the
real one is the integration test you were trying to remove.

### What cannot move at all

Some of this suite's behaviour has no Java-side existence to unit-test:

| Mechanism | Where it lives | Tests that depend on it |
|---|---|---|
| The quota rule itself | `LimitUsageRepository.reserve` — one native `INSERT … ON CONFLICT (resource, subject) DO UPDATE … WHERE limit_usage.period_start <= :cutoff OR limit_usage.used < :max`. Refusal *is* the row count this returns. | **64** `LimitsEnforced` + much of the 55 facade tests |
| Drift repair | `R__recompute_limit_usage.sql`, a repeatable Flyway migration rebuilding `limit_usage` from the permission tables | **27** `RecomputeMigration.run` call sites |
| Optimistic locking | `@Version` + `flush()` + `ObjectOptimisticLockingFailureException` | 9 version/concurrency tests, incl. every `412` |
| Cross-module transaction events | two `@TransactionalEventListener(BEFORE_COMMIT)` (`RecipeService:362`, `MealPlanService:211`) | `shouldConvertEntryToPlaceholderWhenRecipeIsDeleted`, `shouldRemoveOwnedRecipesFromCollectionWhenUnshared` |
| Cross-package JPQL | `RecipeRepository` joins `RecipesCollectionPermission` from another module's package | the collection-visibility tests |

A stub can *simulate* the quota upsert, but then the 64 quota tests verify a Java
`if (used < max)` that no production code path executes. That is not a cheaper
version of the same coverage; it is different coverage.

### The arithmetic

Best case — every one of the ~110 movable plain-web tests becomes a 2 ms unit
test, saving ~37 ms each: **~4 s**, minus the ~1.1 s one-time class-loading cost
per new unit-test class. Realistically **~3 s off a 56 s wall clock**, for ~1 000
lines of unverified reimplementation.

**Verdict: no, at this ratio.**

## Option 3 — Postgres for complicated reads, stubs for the rest

This is the best-reasoned of the three, and its instinct is right: spend the real
database where the database is doing the thinking. But its premise — that a test
on Postgres is expensive — is what the measurements contradict.

The container is a **per-context** cost, and once shared it is ~0 (§"per context").
A test that stays on Postgres costs ~37 ms more than the same test on a stub. So
the option reduces to option 2 with a smaller movable set, and the set it moves is
precisely the one whose integration tests are *already cheapest*:
`LimitsIntegrationTest` at 19 ms and `RecipesCollectionIntegrationTest` at 38 ms
are the simple-read suites; `MealPlanIntegrationTest` at 114 ms — the one with the
calendar projection — is the complicated-read suite that would stay.

There is also a second cost the option would incur and the others would not: two
populations of tests for the same module means two contexts (`@SpringBootTest` for
the complicated reads, plus whatever the stubs need), and rieckpil's guidance is
that
[mixing slice and full-context populations for one area](https://rieckpil.de/spring-boot-test-context-caching-the-complete-guide/)
is the shape that caches worst.

**Verdict: no — it optimises the axis that is already free.**

## What *is* worth extracting, for reasons other than speed

About twenty tests are in the integration suite only by habit:

- **~5 pure-logic validators.** `MealPlanService.validateEntry`'s four rules and
  `MealPlanCalendarService`'s date-range rules are total functions over their
  arguments — no repository, no Spring, no stub. They are the same shape as the
  existing `LimitPeriodTest` and `ProvisioningServiceTest`, which run at 1–2 ms.
  Moving them is free and needs no infrastructure at all.
- **~10 Bean Validation tests.** Genuinely web-layer, but a `@WebMvcTest` context
  costs more than they save. They could equally be asserted against a
  `Validator` directly, with no Spring context whatsoever — which *would* be a
  strict win, at ~2 ms each and zero context.
- **`LimitsModuleArchitectureTest`** — 2 tests, **2.2 s**, the third-most expensive
  class in the suite after the fixes. It is an ArchUnit class-graph scan, unrelated
  to Spring or the database, and `ArchUnit`'s
  [caching by location](https://www.archunit.org/userguide/html/000_Index.html#_caching)
  via `@AnalyzeClasses` would collapse the double scan.

Total: perhaps **2.5 s**, almost all of it from the ArchUnit class rather than from
reclassifying any integration test.

## Why the suite's shape is defensible as it is

Worth naming, since the three proposals all assume the opposite. This is a
CRUD-plus-permissions service where the interesting logic *is* the data access:
sharing, ownership, quota accounting, drift repair. There is very little pure
algorithm — and where there is (`ProvisioningService`, `LimitPeriod`), it already
has fast unit tests. That is the profile the
[testing honeycomb](https://engineering.atspotify.com/2018/01/testing-of-microservices)
describes, where the mass of tests sits at the integration level because the
complexity sits in the interactions rather than inside any one class, and unit
tests are reserved for "complicated algorithms or logic that is easier to test in
isolation."

The suite is also unusually well-behaved about context count: four large web
suites already share one context (companion research, §1), which is the discipline
that makes the shared-container fix pay off at all.

## Options, ranked

| # | Change | Measured | Status |
|---|---|---|---|
| 1 | Disable the 429 retry in the six test `RestClient`s | −25.7 s (companion) | one line each |
| 2 | Mock `S3Client` uniformly in `TestcontainersConfiguration` | −8 s here, and hermeticity | see caveat |
| 3 | Share one PostgreSQL container | −21 s (companion) | blocked on cleanup |
| 4 | Cache the ArchUnit class graph | ~−1 s | independent |
| 5 | Move ~5 pure-logic validators out of `MealPlanIntegrationTest` | ~−0.5 s | correctness-neutral, tidier |
| 6 | Assert Bean Validation against a `Validator`, no Spring | ~−0.4 s | only if a slice is *not* introduced |
| — | Options 1–3 from the request | −0.4 s to +4 s | not recommended |

On #2: the bean must be named something other than `s3Client`, or Spring rejects
the context with `BeanDefinitionOverrideException` against `S3Config`. Declaring
it `@Primary` under a different method name in the already-universally-imported
`TestcontainersConfiguration` keeps the context cache key uniform — scattering
`@MockitoBean S3Client` across individual classes would fork contexts instead.

## Open questions

- **Should the ~10 Bean Validation tests move at all?** Asserting them against a
  `Validator` bean directly is a strict win on time, but it stops covering the
  serialisation and `@Valid` wiring that the HTTP test does cover — e.g.
  `shouldAcceptNullQuantityAndUnit` is partly about Jackson, not about the
  constraint annotations.
- **Is the 114 ms median in `MealPlanIntegrationTest` irreducible?** It is 2–3×
  every other suite after the fixes. Not profiled per-request; the calendar
  projection and the multi-entry fixtures are the obvious candidates.
- **Would `@DataJpaTest` be worth it for the `limits` SQL specifically?** It is the
  one context type measured cheaper than a full one (0.53 s vs 0.96 s), and
  `LimitsIntegrationTest`'s 55 tests already bypass HTTP to hit the facade. Moving
  them to a data slice would keep the real SQL and shed Tomcat — a ~1 s question,
  but the only place in this analysis where a slice is the cheaper option.
- **Does the ArchUnit double-scan have a cause worth fixing?** 2.2 s for two tests
  is high enough that `@AnalyzeClasses` caching may not be the whole story.
- **What is the residual after all fixes?** 23 s of JUnit time over 284 tests is
  ~81 ms per test, well above the 19–39 ms medians of the cheap suites. The gap is
  `@BeforeEach`/`@AfterEach` and the expensive suites; per-test users
  (`cleanup-free-tests.md`) address the teardown half of it.

## Sources

Measurements are from this repository at `0bca31a`, reverted after each
experiment.

- `backend-test-suite-runtime.md` (this directory) — the 8-context/8-container
  baseline, the shared-container experiment, and the production-S3 finding whose
  fix is measured here.
- `cleanup-free-tests.md` (this directory) — the Apache HttpClient 429-retry
  finding and the per-test-user proposal, both prerequisites for the floor above.
- `docs/backend/standards/integration-tests.md` — the current mandate for
  `@SpringBootTest` + `RestClient` + Testcontainers that these options would
  partly replace.
- [Context Caching — Spring Framework reference](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html) — the merged-context-configuration cache key, which is why `@WebMvcTest(X.class)` per controller multiplies contexts.
- [Modularizing Spring Boot — spring.io](https://spring.io/blog/2025/10/28/modularizing-spring-boot/) — the Boot 4 split that moved `@WebMvcTest` and `@DataJpaTest` into per-technology `*-test` starters this project does not depend on.
- [What's New for Testing in Spring Boot 4 and Spring Framework 7 — rieckpil](https://rieckpil.de/whats-new-for-testing-in-spring-boot-4-0-and-spring-framework-7/) — the paired `spring-boot-starter-<x>-test` convention and the new slice package names.
- [Spring Boot Test Context Caching: The Complete Guide — rieckpil](https://rieckpil.de/spring-boot-test-context-caching-the-complete-guide/) — why mixing slice and full-context populations for one area caches worst.
- [A Guide to Efficient Testing in Spring Boot with @DataJpaTest and @WebMvcTest — Obregón](https://medium.com/@AlexanderObregon/a-guide-to-efficient-testing-in-spring-boot-with-datajpatest-and-webmvctest-d8d1eaf2ab95) — the "2–3 s vs 10–15 s" figure this research measured and found to be a statement about container boots, not slices.
- [Software Engineering at Google, ch. 13 — Test Doubles](https://abseil.io/resources/swe-book/html/ch13.html) — a fake is only useful if its behaviour matches the real implementation, and contract tests running the same suite against both are how you establish that.
- [Testing on the Toilet: Don't Mock Types You Don't Own — Google Testing Blog](https://testing.googleblog.com/2020/07/testing-on-toilet-dont-mock-types-you.html) — don't write your own fake for someone else's API unless you can keep it in sync; `JpaRepository` is someone else's API.
- [Fakes are Test Doubles with contracts — ploeh blog](https://blog.ploeh.dk/2023/11/13/fakes-are-test-doubles-with-contracts/) — the same argument framed as the contract a hand-written stub silently assumes.
- [Testing of Microservices — Spotify Engineering](https://engineering.atspotify.com/2018/01/testing-of-microservices) — the honeycomb, and the rule that unit tests are for complicated algorithms rather than for interaction-heavy code.
- [The simplest way to replace H2 with a real database for testing — Testcontainers](https://testcontainers.com/guides/replace-h2-with-real-database-for-testing/) — why a substitute data store proves only that the query is valid against the substitute; applies equally to a hand-written in-memory stub.
- [Testcontainers: Testing with Real Dependencies — Docker](https://www.docker.com/blog/testcontainers-testing-with-real-dependencies/) — the general case that in-memory substitutes give the impression of a working system rather than evidence of one.

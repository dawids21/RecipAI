# Backend Test Suite Runtime — Research

Where the backend test suite's wall clock actually goes, which of the usual
remedies pay off here (measured, not assumed), and what blocks the biggest one.
This is analysis, not an implementation plan.

All numbers were measured on this machine (12 cores, 16 GB RAM, Docker native,
JDK 26, Spring Boot 4.1.0, Surefire default `forkCount=1 / reuseForks=true`) on
branch `21-reduce-backend-test-suite-runtime` at `0bca31a`.

## Summary

The suite is 284 tests and takes **~85 s** (`./mvnw clean test`: 1:29; warm
`./mvnw test`: 1:17–1:23). It splits into **~29 s of Spring context startup**
and **~46 s of test bodies**, with the rest being compile and JVM overhead.

Two findings dominate:

1. **Eight Spring contexts each boot their own PostgreSQL container.** Sharing
   one container is a ~10-line change and measured **89 s → 68 s (−24 %)** — but
   it makes three tests fail, because three suites have no `@AfterEach` and have
   been relying on per-context database isolation to hide it. Cleanup must be
   fixed first; it is the gate on every further speed-up.
2. **Recipe deletion calls the real production S3 bucket during tests.** Every
   `DELETE /recipes/{id}` issues a live `ListObjectsV2` against
   `recipai-data.s3.eu-central-1.amazonaws.com`. It costs 154 ms on average and
   is **4.3 s — 36 % of all server-side time in the suite** — from just 28
   requests. It is also a correctness problem independent of speed.

PostgreSQL durability tuning (`tmpfs`, `fsync=off`) — the advice that turns up
first in every search — was measured at **~1 s** here and is not worth doing.

## Baseline

| Measurement | Value |
|---|---|
| `./mvnw clean test` wall clock | 1:29 (89 s) |
| `./mvnw test` wall clock (warm `target/`) | 1:17 – 1:23 |
| Tests | 284 run, 2 skipped, 0 failures |
| Sum of Surefire test-case times | 45.6 s |
| Sum of Spring context startup times | 29.2 s (8 contexts) |
| PostgreSQL containers started | 8 (+1 Ryuk) |
| HTTP requests made by the suite | 1 178 |
| Total server-side request time | 11.9 s |

Reproduce the per-class breakdown with:

```
./mvnw clean test -B
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET, statistics as st
from collections import defaultdict
g=defaultdict(list)
for f in glob.glob('target/surefire-reports/*.xml'):
    for tc in ET.parse(f).getroot().iter('testcase'):
        g[tc.get('classname')].append(float(tc.get('time')))
for cn in sorted(g, key=lambda c:-sum(g[c])):
    v=g[cn]
    print(f"{sum(v):7.2f}s n={len(v):3d} mean={st.mean(v)*1000:6.0f}ms median={st.median(v)*1000:6.0f}ms {cn}")
EOF
```

### A gotcha before any measurement: always `clean`

The first `./mvnw test` of this session failed with **283 errors**, all of them
`Failed to execute script R__recompute_limit_usage.sql: relation
"recipe_permission" does not exist`. Cause: `target/classes/db/migration` still
held `V20`–`V24` from a different branch, so Flyway applied migrations that drop
tables the repeatable script then reads. Nothing was wrong with the code.
`target/` is not branch-aware — **any timing or green/red judgement must come
from a `clean` run.**

## Where the time goes

### 1. Eight contexts, eight containers

Spring caches an `ApplicationContext` per unique
[merged context configuration](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html);
`@TestPropertySource`, `@SpringBootTest(properties = …)` and the web environment
all form part of that key. `TestcontainersConfiguration` declares the container
as a `@Bean`, and Spring Boot documents that
[container beans are created and started once per application context](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html).
One context therefore means one PostgreSQL container and one full Flyway run
over 20 migrations.

| # | Context (first class to load it) | What makes its key unique | Startup |
|---|---|---|---|
| 1 | `RecipAiApplicationTests` | MOCK web env, no `TestSecurityConfiguration`, no properties | 2.2 s |
| 2 | `LimitsIntegrationTest` | MOCK web env, `limits.enabled=true` | 2.6 s |
| 3 | `LimitsIntegrationTest.Disabled` | `@TestPropertySource(limits.enabled=false)` | 2.3 s |
| 4 | `LimitsApiIntegrationTest` | RANDOM_PORT, `limits.enabled=true` | 2.9 s |
| 5 | `LimitsApiIntegrationTest.Disabled` | `@TestPropertySource(limits.enabled=false)` | 2.4 s |
| 6 | `ExtractionIntegrationTest` | adds `TestAiConfiguration` + `spring.ai…api-key` | 8.5 s |
| 7 | `MealPlanIntegrationTest` | RANDOM_PORT, `limits.enabled=false` — **shared** with `RecipeIntegrationTest`, `ShoppingListIntegrationTest`, `RecipesCollectionIntegrationTest` | 2.4 s |
| 8 | `MealPlanIntegrationTest.LimitsEnforced` | `@TestPropertySource(limits.enabled=true)` — **shared** with the other three `LimitsEnforced` nested classes | 5.8 s |

Two things worth noting. The four big web suites already share one context, so
the annotation discipline in
`docs/backend/standards/integration-tests.md` is working. And context 6 is
expensive only because it is first — it absorbs JVM warm-up and class loading.

Contexts 3 and 5 exist purely because `@TestPropertySource` on a nested class
produces a different cache key than the same value passed through
`@SpringBootTest(properties = …)` on the enclosing class. Context 4 is
configured identically to context 8 in every way that matters at runtime, and
still gets its own container for the same reason.

The default context-cache size is 32, so no eviction is happening — 8 contexts
all stay alive, and so do their 8 containers.

### 2. Server-side: recipe deletion calls production S3

Running the whole suite with Tomcat's access log
(`-Dserver.tomcat.accesslog.pattern='%t %r %s %D'`) gives the definitive
server-side profile. 1 178 requests, 11.9 s total:

| sum | reqs | mean | max | endpoint |
|---|---|---|---|---|
| **4 304 ms** | 28 | **154 ms** | 483 ms | `DELETE /recipes/{id}` → 204 |
| 840 ms | 203 | 4.1 ms | 14 ms | `GET /meal-plans` → 200 |
| 632 ms | 62 | 10.2 ms | 19 ms | `DELETE /meal-plans/{id}` → 204 |
| 601 ms | 58 | 10.4 ms | 60 ms | `POST /recipes` → 201 |
| 587 ms | 62 | 9.5 ms | 53 ms | `POST /meal-plans` → 201 |
| 443 ms | 84 | 5.3 ms | 10 ms | `POST /shopping-lists` → 201 |
| 368 ms | 16 | 23.0 ms | 193 ms | `POST /extract/text` → 200 |
| 172 ms | 10 | 17.2 ms | 37 ms | `POST /extract/text` → 429 |
| 105 ms | 12 | 8.7 ms | 13 ms | `POST /shopping-lists/{id}/items` → 429 |

`DELETE /recipes/{id}` is **15× slower than the next-slowest write** and is 36 %
of all server time from 2 % of the requests. The reason is in
`RecipeService.deleteById` (`RecipeService.java:195`), which ends with
`recipeImagesService.deleteAllImages(id)` →
`S3Service.deleteAllRecipeImages` → `s3Client.listObjectsV2(...)`.

Confirmed directly with `-Dlogging.level.software.amazon.awssdk.request=DEBUG`:

```
software.amazon.awssdk.request : Sending Request: DefaultSdkHttpFullRequest(
  httpMethod=GET, protocol=https,
  host=recipai-data.s3.eu-central-1.amazonaws.com,
  queryParameters=[list-type, prefix])
```

`S3Config` builds the client with `DefaultCredentialsProvider`, and this machine
has `~/.aws/credentials`, so the call **succeeds against the live production
bucket** — which is why nothing shows up in the logs as an error. Two live AWS
round trips were observed for a single test method.

Beyond the 4.3 s, this means the suite is not hermetic: it depends on the
developer's AWS credentials and on network reachability of eu-central-1, and a
test that ever uploaded an image would write into production storage. On a
machine *without* credentials it would be slower still, because the credential
chain probes the EC2 instance-metadata endpoint before giving up.

### 3. Client-side: the quota suites, and what is *not* slow

Per-class breakdown of the 45.6 s of test bodies:

| sum | n | mean | median | max | class |
|---|---|---|---|---|---|
| 11.06 s | 29 | 381 ms | 64 ms | 2 196 ms | `ShoppingListIntegrationTest$LimitsEnforced` |
| 8.68 s | 14 | 620 ms | 492 ms | 1 371 ms | `RecipeIntegrationTest$LimitsEnforced` |
| 6.62 s | 45 | 147 ms | 64 ms | 2 748 ms | `MealPlanIntegrationTest` |
| 6.23 s | 14 | 445 ms | 63 ms | 1 148 ms | `ExtractionIntegrationTest` |
| 4.02 s | 10 | 402 ms | 119 ms | 1 134 ms | `MealPlanIntegrationTest$LimitsEnforced` |
| 3.77 s | 11 | 343 ms | 59 ms | 1 108 ms | `RecipesCollectionIntegrationTest$LimitsEnforced` |
| 1.68 s | 2 | 838 ms | — | 1 675 ms | `LimitsModuleArchitectureTest` |
| 1.41 s | 27 | 52 ms | 36 ms | 351 ms | `RecipeIntegrationTest` |
| 1.05 s | 47 | 22 ms | 21 ms | 50 ms | `ShoppingListIntegrationTest` |
| 0.61 s | 47 | 13 ms | 12 ms | 29 ms | `LimitsIntegrationTest` |
| 0.31 s | 10 | 31 ms | 29 ms | 75 ms | `RecipesCollectionIntegrationTest` |
| < 0.1 s | 28 | — | — | — | remaining unit / arch / nested-`Disabled` classes |

The four `LimitsEnforced` nested classes are **64 tests costing 27.5 s (60 % of
all test-body time)**, against 176 tests in the plain integration suites costing
10.0 s. Per test that is ~430 ms versus ~57 ms.

The obvious hypothesis — that returning a 429 is slow — is **wrong**. The access
log puts `POST /recipes` → 429 at 14.7 ms mean and `POST /extract/text` → 429 at
17.2 ms. What actually makes these tests expensive is their shape: to prove a
quota is enforced a test must create resources up to the quota, and the
`@AfterEach` then lists and deletes every resource for **three** users
(`AUTH_TOKEN`, `AUTH_TOKEN_USER_1`, `AUTH_TOKEN_USER_2`) — so for recipes it
pays the 154 ms S3 tax two or three times per test.

Isolating `RecipeIntegrationTest$LimitsEnforced` reproduces it cleanly:

```
1430ms  shouldMatchUsedCarriedOn429BodyWhenQuotaIsHit
1420ms  shouldCarryNoRetryAfterOnStockRefusal
1369ms  shouldAllowReadAndUpdateWhileOverQuotaButKeepCreationRefused
1339ms  shouldRefuseThirdCreateWithLimitDetails
 400ms  shouldRollBackReservationWhenCreateFailsAfterReserve
 396ms  shouldTrackUsageAcrossCreateAndDelete
```

The four expensive tests are exactly the four that fill the quota (and so delete
2–3 recipes in teardown); the cheap ones create at most one.

Two smaller items also show up: `RecomputeMigration.run(dataSource)` re-parses
and re-executes the repeatable migration on every recompute test, and
`LimitsModuleArchitectureTest` costs 1.7 s to scan the class graph twice.

### 4. What is *not* the bottleneck

- **HTTP round trips in general.** `ShoppingListIntegrationTest` runs 47 tests at
  a 21 ms median with several requests each. `RestClient` over localhost is fine.
- **Database durability.** See experiment B below — ~1 s.
- **Context-cache eviction.** 8 contexts against a default `maxSize` of 32.
- **Surefire forking.** Already the optimal default (`forkCount=1`,
  `reuseForks=true`), one JVM for the whole module.
- **Limits logic itself.** `LimitsIntegrationTest` exercises the facade directly:
  47 tests, 13 ms mean.

## Experiments run

Each was applied to a clean tree, measured, and reverted. The working tree is
back at `0bca31a` with no changes.

### A — one shared PostgreSQL container

`TestcontainersConfiguration` changed to the singleton pattern
([SivaLabs](https://www.sivalabs.in/blog/run-spring-boot-testcontainers-tests-at-jet-speed/)):
a `static final PostgreSQLContainer` started in a static initialiser and handed
back from the `@ServiceConnection` bean method.

| | baseline | experiment A |
|---|---|---|
| Wall clock | 89 s | **68 s (−24 %)** |
| Containers | 8 | 1 |
| Context startup, total | 29.2 s | 12.5 s |
| Context startup, non-first | ~2.2–2.9 s each | **0.45–1.16 s each** |
| Result | green | **3 failures** |

The second-to-last row is the important one. Once the container is shared, an
extra Spring context costs well under a second. **The container was the expense,
not the context** — which changes the priority of the usual "collapse your
contexts" advice: doing that here would save roughly 5 s, while sharing the
container saves ~21 s.

### B — shared container + PostgreSQL durability tuning

Experiment A plus `withTmpFs("/var/lib/postgresql/data")` and
`-c fsync=off -c full_page_writes=off -c synchronous_commit=off`.

Wall clock **67 s** — about 1 s better than A, within noise. The suite's writes
are small and few; commit durability is not on the critical path. This is the
most-recommended trick on the web and it does essentially nothing here.

### C — profiling probes

Tomcat access logging over the full suite (§2) and over
`RecipeIntegrationTest$LimitsEnforced` alone, plus AWS SDK request logging.
These produced the endpoint table and the S3 confirmation.

## The blocker: cleanup is inconsistent

Experiment A's three failures are not flakiness — they are a pre-existing defect
that per-context database isolation has been hiding:

```
RecipesCollectionIntegrationTest.createRecipesCollection » TooManyRequests 429 :
  "Limit for RECIPES_COLLECTION reached (4 of 2 used)"
ShoppingListIntegrationTest.createShoppingList » TooManyRequests 429 :
  "Limit for SHOPPING_LIST reached (26 of 2 used)"
RecipeIntegrationTest.shouldRollBackReservationWhenCreateFailsAfterReserve
  expected: 0 but was: <non-zero>
```

`docs/backend/standards/integration-tests.md` requires `@AfterEach` cleanup, but:

| Suite | Outer class `@AfterEach` | Nested `LimitsEnforced` `@AfterEach` |
|---|---|---|
| `RecipeIntegrationTest` | **none** | yes |
| `RecipesCollectionIntegrationTest` | **none** | yes |
| `ShoppingListIntegrationTest` | **none** | yes |
| `MealPlanIntegrationTest` | yes | yes |
| `ExtractionIntegrationTest` | yes | n/a |
| `LimitsIntegrationTest`, `LimitsApiIntegrationTest` | yes | n/a |

Three outer suites create resources and never delete them. They run with
`recipai.limits.enabled=false`, but — as the standards document itself notes —
**usage is still recorded when the switch is off**. So they leave `limit_usage`
rows behind. Today those rows land in a database the `LimitsEnforced` suites
never see, because those suites get a different context and therefore a
different container. `used = 26` against a quota of 2 is the accumulated debt
becoming visible the moment the database is shared.

This is worth fixing on its own merits, not just as a prerequisite: it is
exactly the missed-release class of bug the limits tests are described as the
last line of defence against.

There is a real tension to resolve here. The standards document deliberately
requires teardown to delete **over HTTP**, so that the release path fires and
leaves `used` at zero as evidence. A blanket `TRUNCATE`-based reset — the
[conventional](https://vladmihalcea.com/clean-up-test-data-spring/) and much
faster approach, and one that would make the shared container safe immediately —
would erase that evidence. A reasonable split: API-driven teardown stays in the
`LimitsEnforced` suites where release *is* the thing under test; a fast
truncate-and-reseed runs in the plain suites, where it is not.

## Options, ranked by measured value

| # | Change | Expected | Risk |
|---|---|---|---|
| 1 | Give the three suites cleanup (and settle the truncate-vs-API question) | 0 s on its own | none — fixes a latent bug |
| 2 | Share one PostgreSQL container across all contexts | **−21 s** | needs #1 |
| 3 | Stop calling real S3 in tests | **−4 s**, plus hermeticity | low |
| 4 | Collapse the 8 contexts toward 1–2 | −5 s | low, once the container is shared |
| 5 | Trim `LimitsEnforced` teardown (3 users → the users the test used) | a few seconds | low |
| 6 | Testcontainers `withReuse(true)` for local iteration | −2–3 s per *repeat* run | needs deterministic cleanup |
| 7 | Parallel execution | potentially large | high — see below |

**On #3**, two shapes are available. A `@TestConfiguration` supplying a
`@Primary` mock `S3Client`, imported uniformly by every integration test — cheap,
and uniform import keeps it out of the context cache key. Or a LocalStack
container via Testcontainers
([guide](https://testcontainers.com/guides/testing-aws-service-integrations-using-localstack/)),
which keeps the S3 code path real at the cost of a second container. Note that
scattering `@MockitoBean S3Client` across individual classes would be the worst
option: bean overrides *are* part of the context cache key, so inconsistent
mocking forks contexts
([rieckpil](https://rieckpil.de/spring-boot-testcontext-cache-best-practices/)).

**On #4**, the cheapest route is to stop expressing the limits kill-switch as a
Spring property in tests. If `LimitsProperties.enabled` were readable from a bean
a test can flip at runtime, every `properties = "recipai.limits.enabled=…"` and
every nested `@TestPropertySource` disappears, and with them contexts 3, 5 and 8.
Worth ~5 s, and it also removes the `@Nested`-class-per-context pattern the
standards document currently prescribes. That is a standards change, so it is the
user's call.

**On #7**, parallelism is not available today at any level. Every suite hard-codes
the same three subjects (`user@example.com`, `user1@example.com`,
`user2@example.com`) and every teardown deletes *all* resources for those
subjects, so two tests running concurrently would delete each other's fixtures.
`forkCount > 1` has the same problem plus a container per fork. Per-test unique
subjects would be the prerequisite; with 284 tests at 85 s it is hard to justify
before options 1–5 are done.

## Open questions

- **Truncate or API teardown for the plain suites?** §"The blocker" lays out the
  trade-off; the standards document takes a deliberate position that a
  truncate-based reset would partly undo.
- **Mock `S3Client` or run LocalStack?** Mocking is faster and simpler; LocalStack
  keeps the image code path honest. No test currently uploads an image, which
  argues for the mock.
- **Is `RecipeIntegrationTest`'s missing teardown load-bearing?** Some of its 27
  tests assert on `getAllRecipes()` results; adding cleanup may change what they
  see. Needs checking test by test.
- **Should `RecipAiApplicationTests.contextLoads` stay?** It is a whole context and
  container to assert something eight other suites already prove. Removing it
  saves a container only until #2 lands, after which it is nearly free.
- **What is the residual ~34 s?** Server-side work is 11.9 s of the 45.6 s of
  test-body time. The rest is JUnit/Spring per-test machinery, in-test JDBC,
  `RecomputeMigration.run`, and Mockito deep-stub resets. It is spread thinly
  rather than concentrated, so it is likely the floor rather than a target — but
  it has not been profiled at method level.

## Sources

- [Context Caching — Spring Framework reference](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html) — the ten parameters that form the context cache key, the default `maxSize` of 32 and its LRU eviction, and the `org.springframework.test.context.cache=DEBUG` switch for cache statistics.
- [Testcontainers — Spring Boot reference](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — confirms container beans are created and started once *per application context*, and that one container instance can be retained across test classes.
- [Spring Boot + Testcontainers Tests at Jet Speed — SivaLabs](https://www.sivalabs.in/blog/run-spring-boot-testcontainers-tests-at-jet-speed/) — the static singleton container pattern used in experiment A, with the author's own 4 → 2 → 1 container progression.
- [Spring Boot TestContext Cache Best Practices — rieckpil](https://rieckpil.de/spring-boot-testcontext-cache-best-practices/) — what forks contexts in practice: profiles, per-class property overrides, inconsistent `@MockitoBean` sets, `@DirtiesContext`.
- [Improved Testcontainers Support in Spring Boot 3.1 — spring.io](https://spring.io/blog/2023/06/23/improved-testcontainers-support-in-spring-boot-3-1/) — the `@ServiceConnection` model this project already uses.
- [Reuse Containers With Testcontainers — rieckpil](https://rieckpil.de/reuse-containers-with-testcontainers-for-fast-integration-tests/) — how `withReuse(true)` plus `~/.testcontainers.properties` works, and why it forces you to own data cleanup (option 6).
- [Faster tests by reusing Testcontainers in Spring Boot — Logarithmic Whale](https://logarithmicwhale.com/posts/faster-tests-by-resuing-testcontainers-in-spring-boot/) — argues singleton containers and `withReuse` both fall short when contexts multiply, and shows a `ContextCustomizerFactory` alternative.
- [Optimize Postgres Containers for Testing — Babak K. Shandiz](https://babakks.github.io/article/2024/01/26/re-015-optimize-postgres-containers-for-testing.html) — the `tmpfs` + `fsync=off` + `full_page_writes=off` recipe measured in experiment B.
- [The best way to clean up test data with Spring and Hibernate — Vlad Mihalcea](https://vladmihalcea.com/clean-up-test-data-spring/) — truncate-and-reseed in `@BeforeEach` as the default cleanup strategy, and why `@Transactional` rollback does not apply to `RANDOM_PORT` HTTP tests.
- [From 4 Minutes to 3 Seconds — dev.to/miry](https://dev.to/miry/from-4-minutes-to-3-seconds-how-database-transaction-rollback-revolutionized-test-suite-4olh) — the ~500 ms-per-test truncate tax that motivates keeping cleanup narrow.
- [Fork Options and Parallel Test Execution — Maven Surefire](https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html) — `forkCount` / `reuseForks` semantics and the `1 / true` default this project already runs.
- [Running JUnit Tests in Parallel with Maven — Baeldung](https://www.baeldung.com/maven-junit-parallel-tests) — the `junit.jupiter.execution.parallel.*` properties that option 7 would need.
- [Testing AWS service integrations using LocalStack — Testcontainers](https://testcontainers.com/guides/testing-aws-service-integrations-using-localstack/) — the LocalStack variant of option 3.
- [Optimizing Spring Boot tests — Tolgee](https://tolgee.io/blog/optimizing-spring-boot-tests) — segregating context-recreating tests, and `spring.main.lazy-initialization=true`; both are marginal here once the container is shared.

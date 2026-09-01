# Reduce Backend Test Suite Runtime — Task Design

**Date:** 2026-08-30

## Summary

Cut the backend suite from ~85 s to roughly 50 s by removing four measured costs: the test
`RestClient` sleeping a second on every asserted 429, live production-S3 calls on recipe deletion,
eight PostgreSQL containers where one suffices, and API-driven `@AfterEach` teardown. The enabling
change is that every test mints its own subject, which removes the shared-state leakage that makes a
shared container fail today. Production code changes in one place only: the `recipes.images` S3
wrapper splits into a thin object-storage seam in `config.s3` that tests substitute, and an
`ImageService` that keeps the image logic.

Measurements throughout are from `research/backend-test-suite-runtime.md`,
`research/cleanup-free-tests.md` and `research/fewer-integration-tests.md`, taken at `0bca31a`.

## Components and responsibilities

### Production (`backend/src/main/java`)

- **`S3Service`** (CREATE, `xyz/stasiak/recipai/config/s3/S3Service.java`) — public interface, the
  object-storage seam: four methods mirroring the S3 operations the application actually issues. No
  AWS SDK type appears in any signature — only `String`, `byte[]`, `List<String>` and `Duration`.
  This is the substitution point for tests.
- **`AwsS3Service`** (CREATE, `xyz/stasiak/recipai/config/s3/AwsS3Service.java`) — package-private
  `@Service implements S3Service`, delegating to `S3Client` and `S3Presigner`. It owns `S3Properties`
  (so the bucket name never crosses the seam), assembles the SDK request objects, and translates
  `S3Exception` into `S3StorageException`. No branching of its own.
- **`S3StorageException`** (MOVE, `recipes/images/exception/` → `config/s3/`) — stays public. It is
  the seam's failure type, so it belongs with the code that raises it. No `@RestControllerAdvice`
  maps it today; `RecipeImagesService` is the only catcher and only its import changes.
- **`ImageService`** (RENAME of today's `S3Service`, `xyz/stasiak/recipai/recipes/images/ImageService.java`)
  — package-private, five methods, signatures unchanged. What stays is the image-specific part:
  `buildImageKey`/`buildThumbnailKey`, the `recipes/<recipeId>/` prefix, the empty-listing branch in
  `deleteAllRecipeImages`, and `ContentType` handling. The per-method try/catch goes —
  `S3StorageException` now propagates to `RecipeImagesService`, which already catches and logs each
  one with `recipeId`/`imageId`, so today's duplicate log line collapses to one.
- **`RecipeImagesService`** (MODIFY) — field type and two imports (`S3Service` → `ImageService`, and
  `S3StorageException`'s new package). No behavioural change.

`S3Config`, `S3Properties` and `LimitsProperties` are untouched. No new configuration property, no
runtime-mutable switch.

### Test infrastructure (`backend/src/test/java`)

- **`IntegrationTest`** (CREATE, `xyz/stasiak/recipai/IntegrationTest.java`) — composed annotation
  carrying the single context configuration every integration test uses. Its uniformity *is* the
  context-collapse mechanism.
- **`LimitsEnabled`** (CREATE, `xyz/stasiak/recipai/LimitsEnabled.java`) — composed annotation
  carrying the one property source that forms the second context.
- **`TestcontainersConfiguration`** (MODIFY) — owns the single static PostgreSQL container for the
  JVM, and `@Import`s the other test configurations so every context receives an identical bean set.
- **`TestS3Configuration`** (CREATE, `xyz/stasiak/recipai/config/s3/TestS3Configuration.java`) —
  `@TestConfiguration` declaring the `@Primary` `S3Service` bean. With the interface public and in
  `config.s3`, this is an ordinary configuration class — no visibility workaround needed to
  `@Import` it from `xyz.stasiak.recipai`.
- **`NoopS3Service`** (CREATE, `xyz/stasiak/recipai/config/s3/NoopS3Service.java`) — package-private
  no-op `S3Service`: uploads and deletes do nothing, `listObjects` returns `List.of()`,
  `presignGetObject` returns a fixed non-null URL string.
- **`TestSecurityConfiguration`** (MODIFY) — stops being a `@TestConfiguration`; the Mockito
  `JwtDecoder` is deleted. Becomes a token factory whose rule mirrors `DevAuthConfig`.
- **`TestRestClients`** (CREATE, `xyz/stasiak/recipai/TestRestClients.java`) — the one place that
  builds a test `RestClient`, so "no automatic retries" is stated once rather than six times.
- **`application-test.yml`** (CREATE, `backend/src/test/resources/`) — overrides the `dev` settings
  tests must not inherit, and holds the settings that previously lived in `properties =` attributes.
- **The six integration suites** (MODIFY) — uniform annotations, fresh subjects per test, teardown
  removed. `LimitsIntegrationTest`, `LimitsApiIntegrationTest` and `ExtractionIntegrationTest`
  additionally invert their nesting (see Data flow).
- **`RecipAiApplicationTests`** (MODIFY) — adopts `@IntegrationTest` so it joins the shared context
  instead of booting its own.

### Documentation

- **`docs/backend/standards/integration-tests.md`** (MODIFY) — the `@AfterEach` mandate and the
  API-driven-teardown rationale are replaced by the fresh-subject rule and the two annotations.
- **`docs/backend/standards/configuration-profiles.md`** (MODIFY) — records that the test suite
  activates `dev` for its decoder, so the bypass `WARN` in test output is expected.
- **`docs/backend/modules/config/module.md`** (MODIFY) — the `config/s3` file tree gains
  `S3Service`, `AwsS3Service` and `S3StorageException`.
- **`docs/backend/modules/recipes/module.md`** (MODIFY) — `S3Service.java` in the `images/` tree
  becomes `ImageService.java`, and `S3StorageException.java` leaves the `exception/` listing.

## Interfaces and method signatures

### The storage seam

```java
// config/s3/S3Service.java — public interface, no AWS type in any signature
public interface S3Service {
    void         putObject(String key, String contentType, byte[] content);
    void         deleteObjects(List<String> keys);
    List<String> listObjects(String prefix);                    // the object keys under the prefix
    String       presignGetObject(String key, Duration expiration);
}

// config/s3/AwsS3Service.java — package-private @Service, delegation only
class AwsS3Service implements S3Service {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;    // supplies the bucket on every request
    // each method: build the SDK request against s3Properties.bucketName(), call the SDK,
    // catch S3Exception -> log -> throw S3StorageException
}
```

Above the seam, `ImageService` keeps today's five methods and their signatures verbatim — the rename
is the whole of its public change:

```java
// recipes/images/ImageService.java — package-private, today's S3Service minus the SDK
class ImageService {
    void   uploadImage(UUID recipeId, UUID imageId, byte[] imageData, ContentType contentType);
    void   uploadThumbnail(UUID recipeId, UUID imageId, byte[] thumbData, ContentType contentType);
    String generatePresignedUrl(String objectKey, Duration expiration);
    void   deleteImage(UUID recipeId, UUID imageId, ContentType contentType);
    void   deleteAllRecipeImages(UUID recipeId);
}
```

```java
// test: config/s3/NoopS3Service.java
class NoopS3Service implements S3Service {
    // putObject, deleteObjects: no-ops
    // listObjects: List.of() — sends deleteAllRecipeImages down its empty-listing branch
    // presignGetObject: a fixed non-null URL string — the value flows into a DTO
}

// test: config/s3/TestS3Configuration.java
@TestConfiguration(proxyBeanMethods = false)
public class TestS3Configuration {
    @Bean @Primary
    S3Service noopS3Service() { return new NoopS3Service(); }
}
```

### Context configuration

```java
@Target(TYPE) @Retention(RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"dev", "test"})
public @interface IntegrationTest {}

@Target(TYPE) @Retention(RUNTIME)
@TestPropertySource(properties = "recipai.limits.enabled=true")
public @interface LimitsEnabled {}
```

```java
@TestConfiguration(proxyBeanMethods = false)
@Import({TestAiConfiguration.class, TestS3Configuration.class})
public class TestcontainersConfiguration {
    private static final PostgreSQLContainer<?> POSTGRES;
    static { POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.5")); POSTGRES.start(); }

    @Bean @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() { return POSTGRES; }
}
```

`application-test.yml` (test classpath only, so it can never reach a deployed jar):

```yaml
logging:
  level:
    xyz.stasiak: INFO
spring:
  ai:
    google:
      genai:
        api-key: test-key
recipai:
  limits:
    enabled: false        # overrides application-dev.yml; nested @LimitsEnabled classes flip it
  s3:
    presigned-url-expiration-minutes: 10
```

### Identity and clients

```java
public final class TestSecurityConfiguration {
    public static String freshToken();              // "u" + UUID without dashes
    public static String emailOf(String token);     // token + "@local.test" — mirrors DevAuthConfig
}

public final class TestRestClients {
    public static RestClient forToken(int port, String token);   // retries disabled
}
```

## Data flow

### Suite startup

1. The JVM starts; the first class to touch `TestcontainersConfiguration` runs its static
   initialiser, which starts **one** PostgreSQL container for the whole run.
2. The first `@IntegrationTest` class builds **context A** (limits off). Flyway runs the 20
   migrations once, against the shared container.
3. The first `@Nested @LimitsEnabled` class builds **context B** (limits on) against the same
   container and the same already-migrated schema.
4. Every later class matches one of those two merged configurations and reuses its context.

### A single test

1. `@BeforeEach` mints fresh tokens — `owner`, and `user1`/`user2` where the test shares.
2. `TestRestClients.forToken(port, owner)` builds a client whose Apache `HttpClient` has
   `disableAutomaticRetries()`. An asserted 429 now costs one round trip, not two plus a 1 s sleep.
3. The request reaches `DevAuthConfig`'s decoder, which turns the bearer token into
   `<token>@local.test`. Ownership rows and `limit_usage` rows are keyed to that string.
4. The test acts and asserts. Recipe deletion runs `ImageService.deleteAllRecipeImages` for real —
   prefix built, empty-listing branch taken — and stops at `NoopS3Service.listObjects`, which returns
   `List.of()` instead of issuing `ListObjectsV2` against `recipai-data`.
5. **No teardown.** Rows survive for the rest of the JVM run and are invisible to every other test,
   because no other test uses that subject.

### The three suites that invert

`RecipeIntegrationTest`, `RecipesCollectionIntegrationTest`, `ShoppingListIntegrationTest` and
`MealPlanIntegrationTest` already have the target shape (limits off outside, on in a nested class)
and only shed their `properties =` attribute. Three suites do not:

- **`LimitsIntegrationTest`** and **`LimitsApiIntegrationTest`** run limits *on* at the outer level
  with a nested `Disabled` class. They flip: the handful of disabled-behaviour tests become the outer
  class, and the ~47 facade tests / the enabled API test move into a `@Nested @LimitsEnabled` class.
- **`ExtractionIntegrationTest`** runs limits on for all 14 tests. The tests asserting a `429` on the
  `EXTRACTION` budget move to a `@Nested @LimitsEnabled` class; the rest stay outer. With limits off
  usage is still recorded, so the non-quota extraction tests are unaffected.

The rule that falls out, and that the standards document will state: **outer classes always run with
limits off; anything that needs them on lives in a `@Nested @LimitsEnabled` class.**

## Pseudo-code

### Fresh subjects in a nested quota class

JUnit runs the enclosing `@BeforeEach` before the nested one, so the nested class can read the
owner the outer class just minted. The `SUBJECT` constant becomes an instance field.

```
outer @BeforeEach:
    owner = freshToken(); user1 = freshToken(); user2 = freshToken()

nested LimitsEnabled @BeforeEach:
    subject = emailOf(owner)
    setLimitQuota(RECIPE, subject, STOCK, 2)     # per-subject override, direct JDBC: no write API
```

The override row is per-subject, so it is inert once the test ends and does not need deleting.

### The release detector, relocated

Today the blanket `assertThat(usedFor(SUBJECT)).isZero()` in teardown catches a missed release
across all 64 quota tests. With a fresh subject it would be trivially true, so it moves into the
tests that actually delete something — three already assert this inline
(`shouldTrackUsageAcrossCreateAndDelete`, `shouldAdmitNextCreateAndDropBalanceAfterDelete`,
`shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare`); the remaining delete-path tests gain it.

```
test shouldTrackUsageAcrossCreateAndDelete:
    id = createRecipe(owner)
    assert usedFor(emailOf(owner)) == 1
    deleteRecipe(owner, id)
    assert usedFor(emailOf(owner)) == 0     # the detector, at the point of the behaviour
```

This is a deliberate narrowing: a net over ~6 tests replaces one over 64. It was chosen with that
trade-off stated.

### State a fresh subject does not isolate

```
three tests named shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow:
    flip limit_config default row (subject IS NULL) STOCK -> FLOW
    try: ...assert...
    finally: restore the default        # NOT @AfterEach — must stay, and blocks parallelism
```

Two more residues, both requiring a rewrite rather than a deletion:

- `ShoppingListIntegrationTest` teardown asserts `COUNT(*) FROM limit_usage WHERE resource =
  'SHOPPING_LIST_ITEM'` is zero — a global check. It must be narrowed to the list UUIDs the test
  created, or dropped.
- `LimitsIntegrationTest` is subject-agnostic: it exercises the facade against synthetic
  `TEST_LIMIT_*` resources. Its lever is a unique **resource** name per test, not a unique subject.
  It already has `newSubject()` / `newResource()` helpers to build on.

## Decisions made

- **Scope is the four measured levers plus context collapse** — 429 retry, S3 stub, fresh subjects +
  shared container, 8 → 2 contexts. ArchUnit caching was dropped from scope: its premise does not
  hold (see Assumptions).
- **Tests authenticate by reusing `DevAuthConfig` via `@ActiveProfiles({"dev", "test"})`** — one
  "accept any token" bypass in the codebase rather than two, and the suite exercises the one
  developers actually use. `application-test.yml` overrides the `dev` settings tests must not
  inherit. Cost: the 129 `@example.com` literals become `@local.test`, which the fresh-subject
  migration was rewriting anyway.
- **Teardown is removed everywhere** — with no shared subject there is nothing to clean. The
  missed-release detector moves inline to the tests that delete.
- **S3 is stubbed at a thin object-storage seam sitting directly on the SDK** — a four-method
  `S3Service` interface whose signatures name only `String`, `byte[]`, `List<String>` and `Duration`,
  with `AwsS3Service` as its one production implementation. Substituting it is equivalent to stubbing
  `S3Client`/`S3Presigner`, but through an interface the project owns: no Mockito deep-stub
  machinery, no SDK response objects to fabricate, and the stub can later become an in-memory fake.
  Keeping AWS types off the boundary is what buys the second of those — a faithful
  `PresignedGetObjectRequest` would need an `expiration`, a *non-empty* `signedHeaders` map and an
  `SdkHttpRequest` (all `Validate`d in `awscore.presigner.PresignedRequest`) invented purely so the
  caller could read one URL back off it. Preferred over LocalStack, which would need a
  `recipai.s3.endpoint` property added to production solely for tests, plus a second container in a
  task about deleting container time.
- **The seam lives in `config.s3`, not in `recipes.images`** — it knows nothing about recipes or
  images, only about object storage, so it belongs beside the `S3Client` and `S3Presigner` beans it
  wraps. `S3Service` is public because `recipes.images` consumes it, exactly as it already consumes
  `S3Properties`; `AwsS3Service`, `NoopS3Service` and `ImageService` stay package-private per the
  visibility rule in `docs/backend/standards/java-patterns.md`. The test configuration gets simpler
  as a side effect: an ordinary `@Bean` of a public type.
- **`AwsS3Service` owns the bucket and the exception translation, `ImageService` owns the keys** —
  the bucket is a property of the storage backend, so it never crosses the seam and `ImageService`
  drops its `S3Properties` dependency; `S3StorageException` moves with the code that raises it. What
  is left is an adapter with no branching and an `ImageService` with no SDK knowledge and no
  try/catch.
- **Context collapse uses uniform annotations, not a production seam** — `LimitsProperties` stays
  immutable. Two composed annotations produce exactly two merged context configurations.
- **The kill-switch default for tests moves to `application-test.yml`** — no integration test carries
  a `properties =` attribute, which is what keeps the outer annotation set identical everywhere.
- **`spring.ai` api-key and `TestAiConfiguration` become universal** — folding them into
  `TestcontainersConfiguration` and `application-test.yml` removes the last per-suite context fork. A
  mock `ChatClient` in every context is correct: no test context should hold a real AI client.
- **Standards are updated in this task** — otherwise the repo's own standards would mis-describe
  every integration test in it.
- **`freshToken()` returns a UUID-based opaque token** rather than a `TestInfo`-derived readable
  name — uniqueness is guaranteed across reruns. Revisit if failure messages prove hard to read.

## Assumptions to verify

- **Assumption:** No `HLD.md` or `requirements.md` exists — the approach was chosen in this design,
  from the research documents and the codebase, rather than agreed upstream.
  **If wrong:** nothing; but the scope decisions above are the only record of what was agreed.

- **Assumption:** The two `@Nested @LimitsEnabled` classes under *different* enclosing classes
  produce equal merged context configurations and therefore share one context.
  **If wrong:** the suite lands at 3–4 contexts instead of 2, costing ~0.5–1 s each. Verify with
  `logging.level.org.springframework.test.context.cache=DEBUG` and read the cache statistics.

- **Assumption:** `@Import` and `@ActiveProfiles` behave as meta-annotations on a composed
  annotation, and `@Nested` classes inherit the enclosing class's composed configuration
  (`@NestedTestConfiguration` defaults to `INHERIT`).
  **If wrong:** the annotations must be applied directly on each class, which still works but loses
  the single point of definition.

- **Assumption:** Spring resolves the `@Primary` `NoopS3Service` bean over the `@Service`-annotated
  `AwsS3Service` without a `BeanDefinitionOverrideException`, since they are distinct bean names
  implementing the same interface.
  **If wrong:** exclude `AwsS3Service` from component scanning in tests, or move its `@Service` to a
  `@Bean` method in a profile-gated configuration.

- **Assumption:** No test asserts on a presigned URL's shape, so `NoopS3Service` can return a fixed
  literal for every key.
  **If wrong:** the stub derives the URL from the key it was given — a one-line change.

- **Assumption:** Dropping the `prod` profile (replaced by `dev` + `test`) loses nothing tests need.
  `application-prod.yml` sets only `SPRING_DATASOURCE_*` placeholders, which `@ServiceConnection`
  supplies, and a presigned-URL expiry that `application.yml` already defaults to 10.
  **If wrong:** add the missing key to `application-test.yml`.

- **Assumption:** Only the two `POST /extract/text` → 429 tests in `ExtractionIntegrationTest` need
  limits on; the other 12 tolerate usage being recorded but never refused.
  **If wrong:** more tests move into the nested class — a larger edit, but no design change.

- **Assumption:** `RecipeIntegrationTest`'s 27 outer tests tolerate the loss of leftover state from
  sibling tests. Their assertions are already `contains(...)` rather than `containsExactly(...)` — a
  shape adopted to tolerate leakage (`RecipeIntegrationTest:363` comments "including those created in
  other tests").
  **If wrong:** individual assertions need adjusting. With fresh subjects they can be *tightened* to
  exact assertions, which is a correctness gain but is not required by this task.

- **Assumption:** The ArchUnit class-graph scan is a single import charged to the first `@ArchTest`,
  not a double scan — the research's own table shows `n=2, mean 838 ms, max 1675 ms`, i.e. one test
  at ~1.7 s and one at ~5 ms. `@AnalyzeClasses` already caches at `CacheMode.FOREVER` and there is
  only one such class, so there is nothing to share with.
  **If wrong:** ~1 s is recoverable by narrowing `packages` to `xyz.stasiak.recipai.limits`, which
  needs verifying against a deliberately introduced violation first. Out of scope here.

- **Assumption:** Unbounded row growth within a run stays harmless. Nothing is deleted, so rows
  accumulate for the whole JVM; `RecomputeMigration.run` rewrites `limit_usage` for every subject at
  each of its 17 call sites.
  **If wrong:** recompute-heavy suites slow as the suite grows. Not a correctness problem — recompute
  rebuilds from the permission tables, which stay truthful for abandoned subjects.

- **Known, accepted:** the image path (`uploadImage`, `uploadThumbnail`, `generatePresignedUrl`,
  `deleteImage`) has no test coverage today, and this task does not add any. What the stub skips is
  narrow, though: everything above the seam still executes — key building, the `recipes/<recipeId>/`
  prefix, the empty-listing branch — and only `AwsS3Service` goes unexercised, i.e. SDK request
  assembly and `S3Exception` translation. Closing the gap properly means a small dedicated
  LocalStack-backed suite that actually uploads — a separate task.

- **Known, accepted:** parallel execution remains blocked after this task by the three tests that
  flip the global `limit_config` default row. They would need `@ResourceLock` or a rewrite to use a
  subject override instead of a default.

## Required reading

- `research/backend-test-suite-runtime.md` — the baseline profile, the eight-container finding, the
  production-S3 call, and the three failures a shared container hits today.
- `research/cleanup-free-tests.md` — the 429-retry finding, the measured `@ActiveProfiles({"dev",
  "test"})` spike, and the one suite already converted to fresh subjects end to end.
- `research/fewer-integration-tests.md` — the per-context cost table that justifies collapsing
  contexts *after* sharing the container rather than instead of it.
- `docs/backend/standards/integration-tests.md` — the teardown mandate and nested-class pattern this
  task replaces; read before rewriting it.
- `docs/backend/standards/configuration-profiles.md` — the profile rules and the bypass warning the
  test suite will now trigger.
- `backend/src/main/java/xyz/stasiak/recipai/config/security/DevAuthConfig.java` — the decoder the
  suite adopts, including why the `@local.test` suffix exists.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/images/S3Service.java` — the class being split
  into a `config.s3` seam and a `recipes.images` `ImageService`. Read with
  `backend/src/main/java/xyz/stasiak/recipai/config/s3/S3Config.java`, which builds the `S3Client`
  and `S3Presigner` beans the seam wraps, and `RecipeImagesService`, its only consumer.
- `docs/backend/standards/java-patterns.md` — the package-private visibility rule that decides which
  of the four new types are public.
- `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — the suite
  closest to the target shape; its outer `@AfterEach` is the one being deleted, not copied.
- `docs/ADRs/0006-shared-limits-module.md` and `docs/backend/modules/limits/module.md` — the opaque
  subject and the config-subject vs usage-subject split that make a fresh subject start clean.

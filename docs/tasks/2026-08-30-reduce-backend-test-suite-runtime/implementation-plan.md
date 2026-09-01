# Reduce Backend Test Suite Runtime — Implementation Plan

**Date:** 2026-09-01

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/integration-tests.md` — the annotation set, the `@AfterEach` mandate and the
  nested-quota pattern this task replaces; it is also one of the files this task rewrites.
- `docs/backend/standards/configuration-profiles.md` — the profile rules, and the `dev` bypass `WARN`
  the suite will start emitting once it activates `dev`.
- `docs/backend/standards/java-patterns.md` — "Package-Private Class Visibility": controllers,
  services and repositories are package-private unless consumed from another package; DTOs crossing
  packages and custom exceptions are public. This decides which of the four new types are public.
- `docs/backend/standards/module-structure.md` — the `exception/` sub-package convention and the
  SLF4J logging patterns `AwsS3Service` must follow when it takes over the `S3Exception` translation.
- `docs/backend/modules/config/module.md` and `docs/backend/modules/recipes/module.md` — the two file
  trees this task edits.

**Design & ADRs**

- `task-design.md` > Components and responsibilities — the authoritative list of what is created,
  renamed, moved and modified.
- `task-design.md` > Interfaces and method signatures — the `S3Service` contract, the two composed
  annotations, and the `application-test.yml` body, all quoted verbatim; copy them rather than
  re-deriving.
- `task-design.md` > Data flow > The three suites that invert — which suites flip their nesting and why.
- `task-design.md` > Pseudo-code — fresh subjects in a nested quota class, the relocated release
  detector, and the state a fresh subject does *not* isolate.
- `docs/ADRs/0006-shared-limits-module.md` and `docs/backend/modules/limits/module.md` — the opaque
  subject and the config-subject vs usage-subject split that make a fresh subject start clean.
- `research/backend-test-suite-runtime.md` — the eight-container table, the production-S3 profile, and
  the three failures a shared container hits today (the acceptance evidence for step 6).
- `research/cleanup-free-tests.md` §2, §3, §5, §6 — the measured `@ActiveProfiles({"dev","test"})`
  spike, the one suite already converted end to end, what the release detector loses, and the
  429-retry finding. Note §6 fixes the retry with Apache's `disableAutomaticRetries()`; step 1 below
  names a JDK factory instead, which avoids Apache entirely — same measured effect, no dependency.
- `research/fewer-integration-tests.md` > Per context — the per-context cost table that justifies
  collapsing contexts *after* sharing the container.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/recipes/images/S3Service.java` — the class being split;
  its five methods, key formats and `S3Exception` catch blocks are the source material for both halves.
- `backend/src/main/java/xyz/stasiak/recipai/config/s3/S3Config.java` — the package `AwsS3Service`
  joins, and the `@Configuration`/`@EnableConfigurationProperties` style beside it.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/images/RecipeImagesService.java` — the only
  consumer of the seam; note it keeps its own `S3Properties` dependency for
  `presignedUrlExpirationMinutes` (only the *bucket* moves behind the seam).
- `backend/src/main/java/xyz/stasiak/recipai/config/security/DevAuthConfig.java` — the decoder the
  suite adopts; `TestSecurityConfiguration.emailOf` must mirror its `@local.test` suffix exactly.
- `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — the suite closest
  to the target shape (limits off outside, on in `LimitsEnforced`); its outer `@AfterEach` at :61 is
  the one being deleted, not copied.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsIntegrationTest.java` — its
  `newSubject()`/`newResource()` helpers are the model for per-test uniqueness in a subject-agnostic
  suite.
- `backend/src/test/java/xyz/stasiak/recipai/TestAiConfiguration.java` — the `@TestConfiguration` +
  `@Bean @Primary` shape `TestS3Configuration` copies.

## File inventory

**Production — `backend/src/main/java/xyz/stasiak/recipai/`**

- **CREATE** `config/s3/S3Service.java` — public four-method object-storage interface, no AWS types.
- **CREATE** `config/s3/AwsS3Service.java` — package-private `@Service`, SDK delegation and exception translation.
- **CREATE** `config/s3/S3StorageException.java` — the moved public seam failure type.
- **DELETE** `recipes/images/exception/S3StorageException.java` — moved to `config/s3/`.
- **CREATE** `recipes/images/ImageService.java` — today's `S3Service` minus the SDK, keys and branching only.
- **DELETE** `recipes/images/S3Service.java` — split into the two files above.
- **MODIFY** `recipes/images/RecipeImagesService.java` — field type and two imports; no behaviour change.

**Test — `backend/src/test/java/xyz/stasiak/recipai/`**

- **CREATE** `IntegrationTest.java` — composed annotation: `@SpringBootTest(RANDOM_PORT)`, `@Import`, `@ActiveProfiles`.
- **CREATE** `LimitsEnabled.java` — composed annotation carrying the one limits-on property source.
- **CREATE** `TestRestClients.java` — the single retry-disabled `RestClient` factory.
- **CREATE** `config/s3/TestS3Configuration.java` — `@TestConfiguration` declaring the `@Primary` `S3Service`.
- **CREATE** `config/s3/NoopS3Service.java` — package-private no-op storage stub.
- **MODIFY** `TestcontainersConfiguration.java` — one static JVM-wide container; imports the AI and S3 test configs.
- **MODIFY** `TestSecurityConfiguration.java` — Mockito `JwtDecoder` deleted; becomes a token factory.
- **MODIFY** `RecipAiApplicationTests.java` — adopts `@IntegrationTest`.
- **MODIFY** `recipes/RecipeIntegrationTest.java` — annotations, fresh subjects, teardown removed, inline detectors.
- **MODIFY** `recipes/collections/RecipesCollectionIntegrationTest.java` — same.
- **MODIFY** `shoppinglists/ShoppingListIntegrationTest.java` — same, plus the global item-usage count narrowed.
- **MODIFY** `planning/MealPlanIntegrationTest.java` — same.
- **MODIFY** `permissions/InviteIntegrationTest.java` — same; its resource-tracking teardown goes too.
- **MODIFY** `extraction/ExtractionIntegrationTest.java` — same, plus nesting inverted.
- **MODIFY** `limits/LimitsIntegrationTest.java` — nesting inverted; per-test resource names; teardown removed.
- **MODIFY** `limits/LimitsApiIntegrationTest.java` — nesting inverted; fresh subjects; teardown removed.

**Test resources**

- **CREATE** `backend/src/test/resources/application-test.yml` — logging, AI key, limits kill-switch, presign expiry.

`backend/pom.xml` is untouched — `JdkClientHttpRequestFactory` is part of `spring-web`.

**Documentation** (named as deliverables in `task-design.md`)

- **MODIFY** `docs/backend/standards/integration-tests.md` — fresh-subject rule and the two annotations replace the teardown mandate.
- **MODIFY** `docs/backend/standards/configuration-profiles.md` — records that tests activate `dev`, so the bypass `WARN` is expected.
- **MODIFY** `docs/backend/modules/config/module.md` — `config/s3` tree gains three files.
- **MODIFY** `docs/backend/modules/recipes/module.md` — `S3Service.java` becomes `ImageService.java`; `S3StorageException.java` leaves `exception/`.

## Step-by-step plan

Steps 1–3 are independent of each other and of the rest. Steps 4→5→6 are strictly ordered: the shared
container is unsafe until fresh subjects land. Step 7 is worth little before step 6 and is safe after it.

1. **Stop the test clients retrying 429** — introduce `TestRestClients.forToken(port, token)` building a
   `RestClient` over an explicit `new JdkClientHttpRequestFactory()`, and route all seven
   `RestClient.builder()` call sites through it. Nothing else changes; the existing `AUTH_TOKEN*`
   constants stay. The retry is Apache's, not `RestClient`'s: `httpclient5` is on the test classpath as a
   runtime transitive of the AWS SDK, `RestClient` auto-detects it when no factory is set, and Apache's
   `DefaultHttpRequestRetryStrategy` treats 429 as retriable. Naming any other factory removes the
   behaviour rather than disabling it. `JdkClientHttpRequestFactory` ships in `spring-web` (7.0.8), so
   this needs no dependency change, and the JDK's `HttpClient` has no status-code retry at all. The
   suite only issues GET/POST/PUT/DELETE plus one multipart POST, all of which it handles.
   - Files: `TestRestClients.java` (new), and the `restClient(String)` helper in
     `recipes/RecipeIntegrationTest.java:56`, `recipes/collections/RecipesCollectionIntegrationTest.java:51`,
     `shoppinglists/ShoppingListIntegrationTest.java:53`, `planning/MealPlanIntegrationTest.java:55`,
     `extraction/ExtractionIntegrationTest.java:112`, `permissions/InviteIntegrationTest.java:56`,
     `limits/LimitsApiIntegrationTest.java:45`.
   - Verify: `cd backend && ./mvnw clean test` — 284 tests, 0 failures; no
     `HttpRequestRetryExec … will be automatically re-executed` line in the output
     (`./mvnw clean test 2>&1 | grep -c HttpRequestRetryExec` returns 0), and no
     `org.apache.hc` import anywhere under `backend/src/test`. Expect the Surefire test-case total to
     fall by roughly 25 s.

2. **Split the S3 wrapper into a seam and an image service (production only)** — create the public
   `config/s3/S3Service` interface, the package-private `config/s3/AwsS3Service` implementing it against
   `S3Client`/`S3Presigner`/`S3Properties`, and move `S3StorageException` into `config/s3`. Rename
   `recipes/images/S3Service` to `ImageService`, drop its `S3Properties` field, its SDK imports and its
   five try/catch blocks, and point it at the seam. Update `RecipeImagesService`'s field type and two
   imports. Behaviour is unchanged except that the duplicate error log collapses to the one
   `RecipeImagesService` already writes.
   - Files: `config/s3/S3Service.java`, `config/s3/AwsS3Service.java`, `config/s3/S3StorageException.java`,
     `recipes/images/ImageService.java`, `recipes/images/S3Service.java` (deleted),
     `recipes/images/exception/S3StorageException.java` (deleted), `recipes/images/RecipeImagesService.java`.
   - Verify: `./mvnw clean test` — compiles, 284 green (the suite still reaches real S3 at this point).
     `grep -rn "software.amazon.awssdk" backend/src/main/java/xyz/stasiak/recipai/recipes/` returns nothing.

3. **Stub the seam in tests** — add `config/s3/NoopS3Service` (uploads and deletes no-op, `listObjects`
   returns `List.of()`, `presignGetObject` returns a fixed non-null URL) and `config/s3/TestS3Configuration`
   declaring it `@Primary`. `@Import` it from `TestcontainersConfiguration` so every context that already
   imports the container picks it up with no per-suite edit.
   - Files: `config/s3/NoopS3Service.java`, `config/s3/TestS3Configuration.java`, `TestcontainersConfiguration.java`.
   - Verify: `./mvnw clean test` — 284 green, and the run makes no S3 call:
     `./mvnw clean test -Dserver.tomcat.accesslog.enabled=true -Dserver.tomcat.accesslog.pattern='%t %r %s %D'`
     shows `DELETE /recipes/{id}` at single-digit ms rather than ~154 ms mean. Confirm no
     `recipai-data.s3.eu-central-1.amazonaws.com` traffic (run with the network to AWS blocked, or check
     the SDK debug log is silent).

4. **Adopt `DevAuthConfig` for test authentication** — add `backend/src/test/resources/application-test.yml`
   exactly as quoted in `task-design.md` > Interfaces. Strip `TestSecurityConfiguration` down to a final
   class holding `AUTH_TOKEN` = `"user"`, `AUTH_TOKEN_USER_1` = `"user1"`, `AUTH_TOKEN_USER_2` = `"user2"`,
   plus `freshToken()` and `emailOf(String)`; delete the Mockito `JwtDecoder` and the `@TestConfiguration`.
   Replace `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class, …})` with
   `@Import(TestcontainersConfiguration.class)` + `@ActiveProfiles({"dev", "test"})` on each suite, and
   rewrite the 182 `@example.com` occurrences as `emailOf(...)` expressions. Subjects are still shared and
   teardown still runs — this step is auth only.
   - Files: `backend/src/test/resources/application-test.yml`, `TestSecurityConfiguration.java`, and all
     seven integration suites.
   - Verify: `./mvnw clean test` — 284 green. `grep -rn "@example.com" backend/src/test` returns nothing.
     `grep -c "AUTHENTICATION BYPASS ENABLED" ` over the run output equals the context count (8 at this
     point). No `DEBUG` line from `xyz.stasiak` in the output, proving `application-test.yml` beats
     `application-dev.yml`.

5. **Fresh subject per test; teardown deleted** — one commit per suite. In each: an outer `@BeforeEach`
   minting `owner` (and `user1`/`user2` where the suite shares) via `freshToken()`; the nested class's
   `SUBJECT` constant becomes an instance field assigned `emailOf(owner)` in its own `@BeforeEach`; every
   `@AfterEach` is deleted. Per-suite specifics:
   - `RecipesCollectionIntegrationTest` first — `research/cleanup-free-tests.md` §3 already proved this
     exact conversion green.
   - `RecipeIntegrationTest`, `MealPlanIntegrationTest`, `ShoppingListIntegrationTest` next.
   - `InviteIntegrationTest`: the `createdResources` list and its `permissionsFacade.resourceDeleted`
     teardown go; `SHARER`/`INVITEE`/`STRANGER` become per-test tokens.
   - `LimitsIntegrationTest`: subject-agnostic — its lever is `newResource()` per test, which it already
     has; delete the `TEST_LIMIT_%` teardown and make sure every test calls `newResource()`.
   - `LimitsApiIntegrationTest`: `SUBJECT` becomes `emailOf(owner)`; the RECIPE-override teardown goes.
   - `ExtractionIntegrationTest`: `SUBJECT`/`SUBJECTS` become per-test tokens; the `limit_usage` /
     `limit_config` teardown goes. Keep the `Mockito.reset(...)` stubbing in `@BeforeEach`.
   - **Keep** the four `shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow` `finally` blocks that
     restore the global `limit_config` default row — a fresh subject does not isolate them.
   - **Relocate the release detector**: `assertThat(usedFor(...)).isZero()` moves inline into the tests
     that delete (list in Test plan below).
   - **Narrow the global count**: `ShoppingListIntegrationTest$LimitsEnforced`'s
     `SELECT COUNT(*) FROM limit_usage WHERE resource = 'SHOPPING_LIST_ITEM'` becomes per-list assertions
     via the existing `usedForItem(listId)` helper, inside `shouldClearItemUsageWhenTheListIsDeleted` and
     `shouldAdmitNextItemAfterDeletingOne`.
   - Files: the seven integration suites.
   - Verify after each suite: `./mvnw test -Dtest=<Suite>` green alone; after the last one
     `./mvnw clean test` — 284 green. `grep -rn "@AfterEach" backend/src/test` returns nothing.

6. **One PostgreSQL container for the JVM** — turn `TestcontainersConfiguration`'s `@Bean` into a static
   field started in a static initialiser and returned by the `@Bean` method, per `task-design.md` >
   Interfaces.
   - Files: `TestcontainersConfiguration.java`.
   - Verify: `./mvnw clean test` — 284 green, and specifically the three tests that fail today under a
     shared container now pass: `RecipesCollectionIntegrationTest#createRecipesCollection`,
     `ShoppingListIntegrationTest#createShoppingList`,
     `RecipeIntegrationTest#shouldRollBackReservationWhenCreateFailsAfterReserve`. Count containers with
     `docker events --filter 'event=start' --filter 'type=container'` running alongside the build: one
     `postgres:17.5` plus Ryuk, not eight.

7. **Collapse to two contexts** — add `@IntegrationTest` and `@LimitsEnabled`; `@Import` `TestAiConfiguration`
   from `TestcontainersConfiguration` so no suite forks a context for it; move
   `spring.ai.google.genai.api-key` and `recipai.limits.enabled=false` out of every `properties =` attribute
   and into `application-test.yml`. Apply `@IntegrationTest` on all seven suites plus
   `RecipAiApplicationTests`, and `@LimitsEnabled` on each nested quota class. Invert the three suites that
   run limits-on at the outer level: in `LimitsIntegrationTest` and `LimitsApiIntegrationTest` the current
   `Disabled` nested classes become the outer bodies and the remaining tests move into a
   `@Nested @LimitsEnabled` class; in `ExtractionIntegrationTest` the tests asserting a 429 move into a
   `@Nested @LimitsEnabled` class and the rest stay outer.
   - Files: `IntegrationTest.java`, `LimitsEnabled.java`, `TestcontainersConfiguration.java`,
     `backend/src/test/resources/application-test.yml`, `RecipAiApplicationTests.java`, all seven suites.
   - Verify: `./mvnw clean test` — 284 green. `grep -rn "properties = " backend/src/test` returns nothing.
     `./mvnw clean test -Dlogging.level.org.springframework.test.context.cache=DEBUG 2>&1 | grep "size = "`
     ends at `size = 2`, and the "Spring test ApplicationContext cache statistics" line reports 2 contexts
     with hit counts covering the rest.

8. **Update the four documents** — rewrite `integration-tests.md` around `@IntegrationTest`,
   `@LimitsEnabled`, `TestRestClients`, the fresh-subject rule and the "outer runs limits off" rule; note
   in `configuration-profiles.md` that the test suite activates `dev` for its decoder so the bypass `WARN`
   is expected in test output; add the three new files to `config/module.md`'s tree and update
   `recipes/module.md`'s `images/` tree.
   - Files: the four documents in the inventory.
   - Verify: every code fence in `integration-tests.md` matches a real file in `backend/src/test`; the two
     file trees match `find` output for their packages.

## Test plan

This task adds no product behaviour, so the "test plan" is the shape the existing suite must end up in
plus the assertions that move.

**Unit tests**

_N/A — no new production logic. `AwsS3Service` is pure delegation and is deliberately left uncovered
(`task-design.md` > Assumptions to verify, "Known, accepted")._

**Integration tests — relocated release detectors** (added inline, in the `LimitsEnforced` /
`@LimitsEnabled` nested class of each suite)

- `RecipeIntegrationTest` — `shouldTrackUsageAcrossCreateAndDelete` (already inline),
  `shouldAdmitNextCreateAndDropBalanceAfterDelete` (already inline),
  `shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare` (already inline),
  `shouldRollBackReservationWhenCreateFailsAfterReserve` and `shouldClearUsageForSubjectThatOwnsNothing`
  gain `assertThat(usedFor(subject)).isZero()` at the point of the release.
- `RecipesCollectionIntegrationTest` and `MealPlanIntegrationTest` — `shouldTrackUsageAcrossCreateAndDelete`,
  `shouldAdmitNextCreateAndDropBalanceAfterDelete`, `shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare`.
- `ShoppingListIntegrationTest` — the same three (`shouldTrackListUsageAcrossCreateAndDelete`), plus the
  item-side replacements for the deleted global count: `shouldClearItemUsageWhenTheListIsDeleted` asserts
  `usedForItem(list.id())` is zero after the list is deleted, and `shouldAdmitNextItemAfterDeletingOne`
  asserts the per-list balance drops by one.

**Integration tests — suites whose nesting inverts**

- `LimitsIntegrationTest` — outer body becomes today's 8 `Disabled` tests; the other ~47 facade tests move
  into `@Nested @LimitsEnabled`. Every test must call `newResource()` so nothing collides across the run.
- `LimitsApiIntegrationTest` — outer keeps `shouldReturnEmptyArrayWhenLimitsAreDisabled`;
  `shouldReturnQuotasResolvedForCallerEmail` and `shouldReflectSubjectOverrideRatherThanDefault` move into
  `@Nested @LimitsEnabled`.
- `ExtractionIntegrationTest` — **three** tests move into `@Nested @LimitsEnabled`, not two:
  `shouldReturn429WithProblemDetailsOnThirdCallAtSeededLimit`, `shouldReturn429WithNoRetryAfterHeaderOrBodyKey`
  and `shouldAdmitNextCallWithNoRestartAfterRaisingQuota` (it asserts a 429 before raising the quota).
  The three `GET /extract/balance` tests stay outer — `LimitsFacade.getBalance` still reports with the
  switch off, as `LimitsIntegrationTest#shouldStillReportBalanceWhenLimitsAreDisabled` proves.

**Integration tests — the three that prove the shared container is safe**

- `RecipesCollectionIntegrationTest#createRecipesCollection`, `ShoppingListIntegrationTest#createShoppingList`,
  `RecipeIntegrationTest#shouldRollBackReservationWhenCreateFailsAfterReserve` — green after step 5+6.
  These are the three that fail today under experiment A of `research/backend-test-suite-runtime.md`.

**Flutter widget/integration tests**

_N/A — no mobile code changes._

**Manual verification**

- Confirm no AWS traffic during a run (step 3's verify), since the current suite hits `recipai-data` for real.
- Read the context-cache DEBUG statistics once at the end of step 7 and record the final context count.
- Record `./mvnw clean test` wall clock before step 1 and after step 8 for the task's own outcome claim.

## Verification checklist

- [ ] `cd backend && ./mvnw clean test` — 284 run, 2 skipped, 0 failures (always `clean`: `target/classes/db/migration` is not branch-aware).
- [ ] Wall clock is at or below ~50 s, down from ~85 s.
- [ ] Exactly one `postgres:17.5` container starts per run.
- [ ] Context cache DEBUG reports 2 contexts.
- [ ] `grep -rn "@AfterEach" backend/src/test` is empty.
- [ ] `grep -rn "@example.com\|properties = " backend/src/test` is empty.
- [ ] No `HttpRequestRetryExec` line in the run output.
- [ ] No request reaches `recipai-data.s3.eu-central-1.amazonaws.com` during a run.
- [ ] `grep -rn "software.amazon.awssdk" backend/src/main/java/xyz/stasiak/recipai/recipes/` is empty.
- [ ] Both ArchUnit suites still pass (`LimitsModuleArchitectureTest`, `PermissionsModuleArchitectureTest`).
- [ ] The four documents in the inventory match the code they describe.
- [ ] `task-design.md` > Assumptions to verify are each confirmed or explicitly deferred — in particular
      the merged-configuration assumption (two nested `@LimitsEnabled` classes share one context) and the
      `@Primary` bean-resolution assumption.
- [ ] No new compiler warnings.

## Risks surfaced during planning

- **Risk:** `MealPlanIntegrationTest` also has a `shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow`
  (`planning/MealPlanIntegrationTest.java:1624`), so **four** tests flip the global `limit_config` default
  row, not the three named in `task-design.md` and both research documents.
  **Why it matters:** one more `finally`-restore survives the teardown purge, and one more test blocks
  parallel execution than the design's "Known, accepted" note allows for.
  **Mitigation:** keep all four `finally` blocks in step 5; correct the count wherever the standards
  document mentions it. No design change — the rule is the same, the population is one larger.

- **Risk:** three `ExtractionIntegrationTest` tests need limits on, not two —
  `shouldAdmitNextCallWithNoRestartAfterRaisingQuota` (`extraction/ExtractionIntegrationTest.java:206`)
  asserts a 429 before raising the quota, alongside the two the design names.
  **Why it matters:** leaving it outer makes it fail silently-then-loudly at the `fail("Should have thrown
  exception")` line.
  **Mitigation:** move all three into the `@Nested @LimitsEnabled` class. This is the design's own
  "if wrong: more tests move into the nested class" branch — resolved, not open.

- **Risk:** the 429 retry comes back the moment a `RestClient` is built without an explicit request
  factory, because `httpclient5` stays on the test classpath as a runtime transitive of
  `software.amazon.awssdk:apache5-client:2.52.0` and `RestClient` auto-detects Apache first.
  **Why it matters:** the regression is silent — a new suite that hand-rolls `RestClient.builder()`
  quietly reintroduces a 1 s sleep per asserted refusal, and nothing fails.
  **Mitigation:** `TestRestClients` is the single construction point (step 1) and the standards rewrite
  in step 8 states it as the rule; the `grep -c HttpRequestRetryExec` check in the verification
  checklist catches a violation in one run.

- **Risk:** `LimitsIntegrationTest` and `RecipAiApplicationTests` currently run with the MOCK web
  environment; `@IntegrationTest` moves both to `RANDOM_PORT`.
  **Why it matters:** they gain a Tomcat start they do not use. That is the price of a single merged
  configuration and is what makes them join contexts A/B rather than fork two more.
  **Mitigation:** none needed — the alternative (a MOCK-env variant annotation) reintroduces the fork this
  task exists to remove. Confirm in step 7's cache statistics that neither class opens a third context.

- **Risk:** the `@example.com` rename is wider than the research states — 182 occurrences across 8 files
  (research counted 129), and 201 `AUTH_TOKEN*` references.
  **Why it matters:** step 4 is the largest mechanical edit in the task and the easiest place to introduce
  a silent mismatch between a share target and a caller identity.
  **Mitigation:** do step 4 file by file, running that suite alone after each; a mismatched identity surfaces
  as a 403 or an empty listing, not a compile error.

- **Risk:** with no teardown and one container, `limit_usage` grows for the whole JVM while
  `RecomputeMigration.run` — called from 17 test sites — rewrites usage for *every* subject each time.
  **Why it matters:** the recompute-heavy tests get slower as the suite grows, partially eating the win.
  **Mitigation:** already an accepted assumption in the design; measure the recompute tests' Surefire times
  before and after step 5 and report them if they have moved materially.

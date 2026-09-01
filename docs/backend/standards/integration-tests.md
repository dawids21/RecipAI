# Integration Test Pattern

### Spring Boot Integration Tests with Testcontainers + RestClient
All backend integration tests use the following pattern:

- `@IntegrationTest` — a composed annotation (`xyz.stasiak.recipai.IntegrationTest`) carrying
  `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Import(TestcontainersConfiguration.class)` and
  `@ActiveProfiles({"dev", "test"})`. `TestcontainersConfiguration` owns the one PostgreSQL container
  for the whole JVM and imports the AI and S3 test configurations, so every `@IntegrationTest` class
  gets an identical bean set and merges into the same Spring context. Applying it uniformly *is* the
  mechanism that keeps the suite down to two contexts (see below) — never add a class-level
  `properties = ` attribute, `@TestPropertySource`, or an ad hoc `@Import`, each of which forks a new
  context and a new container.
- `TestRestClients.forToken(port, token)` (not MockMvc) — the one place a test `RestClient` is built,
  with retries disabled via an explicit `JdkClientHttpRequestFactory`. Never call
  `RestClient.builder()` directly in a test: `httpclient5` is on the classpath as a transitive of the
  AWS SDK, and `RestClient` auto-detects it and retries a 429 after a one-second sleep unless a factory
  is named explicitly.
- A fresh subject per test, minted via `TestIdentities.freshToken()`
  (`"u" + UUID` mapped by `DevAuthConfig` to `<token>@local.test`) and passed to `TestRestClients`. No
  `@AfterEach` teardown: with no shared subject there is nothing to clean up, and no other test can see
  a leftover row. Reuse the same fresh subject with `TestIdentities.emailOf(token)` wherever
  the test needs the caller's email directly (a share target, a facade call, an assertion).
- AssertJ for assertions
- JUnit 5 `@Test` annotations
- Test method naming: `shouldXxxWhenYyy` (camelCase)

```java
@IntegrationTest
class RecipeIntegrationTest {

    @LocalServerPort
    private int port;

    private String owner;

    @BeforeEach
    void freshUser() {
        owner = TestIdentities.freshToken();
    }

    private RestClient restClient() {
        return TestRestClients.forToken(port, owner);
    }

    @Test
    void shouldReturnNotFoundWhenRecipeDoesNotExist() {
        ...
    }
}
```

A suite that shares a subject across several clients (owner, an invitee, a stranger) mints one fresh
token per role in the same `@BeforeEach`, the way `RecipeIntegrationTest` mints `owner`, `user1` and
`user2`.

### Seed and Read Through the Module's Own Methods

Prefer the module's own business surface — an HTTP call, a facade method — for **seeding** a fixture as
well as for **reading back** what the code under test did. A test that creates its state the way the
application does exercises the real path, and one that reads through the facade cannot drift from what
the module actually stores.

```java
// Correct: read the balance through the facade
private int usedFor(String subject) {
    return limitsFacade.getBalance(subject, ExtractionService.EXTRACTION_RESOURCE)
            .map(LimitBalance::used)
            .orElse(0);
}

// Wrong: reach around the module into its tables
jdbcClient.sql("SELECT used FROM recipai.limit_usage WHERE ...").query(Integer.class).single();
```

Adding a read method widens a module's public API for a test's benefit, so **always tell the developer
when you do it** — it is their call whether the method belongs there. `LimitsFacade.getBalance` exists
for exactly this reason.

Reach for `JdbcClient` only when **no business path can produce the state** — fabricating impossible
state for a drift-repair test (a `used` no business path could have written, or a usage row for a
subject with no API presence at all), or seeding a `limit_config` row, which has no write API at all —
and leave a one-line comment saying why.

Everything else — creating the resource whose count is being asserted, deleting it again, raising a
quota — goes through the API or the facade.

### Testing a Suite Whose Module Is Limited by `limits`

`recipai.limits.enabled` is `false` for every outer `@IntegrationTest` class (set in
`application-test.yml`), so a suite that creates several of a limited resource stays about the module's
own behaviour by default. Turning the flag off does not stop usage from being recorded, only from being
refused — a fresh subject never accumulates enough usage to hit a real quota by accident, so plain
suites need no special handling at all.

**The rule that falls out of this: outer classes always run with limits off; anything that needs them
on lives in a `@Nested @LimitsEnabled` class.** `@LimitsEnabled` (`xyz.stasiak.recipai.LimitsEnabled`)
is a composed `@TestPropertySource(properties = "recipai.limits.enabled=true")` — applying it uniformly
on every quota-testing nested class is what lets all of them share one second Spring context instead of
forking one each.

```java
@Nested
@LimitsEnabled
class LimitsEnforced {

    private String ownerSubject;

    @BeforeEach
    void setUpQuota() {
        ownerSubject = TestIdentities.emailOf(owner);   // owner is minted by the outer @BeforeEach,
        setLimitQuota("RECIPE", ownerSubject, 2);                  // which JUnit always runs first
    }

    ...
}
```

**Set the nested class's own `limit_config` override** for its subject rather than relying on the
shipped default, so the test does not break when an operator changes a production number. No teardown
is needed for the override row: it is scoped to the fresh subject and is inert once the test ends.

Because every subject is fresh, a blanket "assert usage is zero" check at the end of a quota test is
vacuous: with nothing else touching the subject, a missing release and a correct one look identical
from the outside. **The release detector belongs inline**, in the handful of tests that actually
delete something:

```java
@Test
void shouldTrackUsageAcrossCreateAndDelete() {
    RecipeDetailsDto recipe = createRecipe(client, "Recipe", data, null);
    assertThat(usedFor(ownerSubject)).isEqualTo(1);

    deleteRecipe(client, recipe.id());
    assertThat(usedFor(ownerSubject)).isZero();      // the detector, at the point of the behaviour
}
```

These targeted assertions are the suite's defence against a missed release, and they sit at the point
of the behaviour they guard — the delete — where the assertion can only pass if release actually
fired.

**One thing a fresh subject does not isolate**: a test that flips the `limit_config` **default** row
(`subject IS NULL`) mutates global state no per-subject scheme reaches. Such a test must still restore
the default itself, in a `finally` block — not `@AfterEach`, since the restore is part of the test's own
correctness, and it is the one shape that blocks parallel execution:

```java
@Test
void shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow() {
    setLimitQuota("RECIPE", null, "FLOW", 5);
    try {
        ...
    } finally {
        setLimitQuota("RECIPE", null, "STOCK", 5);   // restore the shipped default
        limitsFacade.clear(subject, RecipeService.RECIPE_RESOURCE);   // and the usage it accrued
    }
}
```

A suite that is subject-agnostic (exercises a facade against synthetic resource keys rather than real
callers, like `LimitsIntegrationTest`) has no subject to freshen at all; its lever is a unique
**resource** name per test instead — `newResource()` / `newSubject()` helpers that return a
UUID-suffixed string.

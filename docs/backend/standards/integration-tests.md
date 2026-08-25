# Integration Test Pattern

### Spring Boot Integration Tests with Testcontainers + RestClient
All backend integration tests use the following pattern:

- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` — starts the full app
- `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})` — real PostgreSQL container, mock security
- `RestClient` (not MockMvc) to make HTTP calls against the running application
- `@AfterEach` to clean up test data
- AssertJ for assertions
- JUnit 5 `@Test` annotations
- Test method naming: `shouldXxxWhenYyy` (camelCase)

```java
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeIntegrationTest {
    RestClient client;

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("Authorization", "Bearer test-token")
            .build();
    }

    @Test
    void shouldReturnNotFoundWhenRecipeDoesNotExist() {
        ...
    }

    @AfterEach
    void tearDown() { ... }
}
```

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

Reach for `JdbcClient` only when **no business path can produce the state**, and leave a one-line
comment saying why. Two cases in this codebase qualify:

- **`limit_config` has no write API.** Operators edit it with SQL, so a test that needs a quota writes
  one the same way. Every limits suite has a private `setLimitQuota(...)` helper for this — an
  **upsert** (`ON CONFLICT (resource, subject) DO UPDATE`, valid against
  `UNIQUE NULLS NOT DISTINCT (resource, subject)`) so a test can set a quota whether or not a row is
  already there, and so raising or lowering one mid-test is the same call as seeding it.
- **Drift-repair tests must fabricate impossible state.** A recompute is only worth testing against a
  `used` no business path could have written (`SET used = 99`), or a usage row for a subject with no
  API presence at all. Teardown of rows no API deletes belongs in the same category.

Everything else — creating the resource whose count is being asserted, deleting it again, raising a
quota — goes through the API or the facade.

### Testing a Suite Whose Module Is Limited by `limits`

`recipai.limits.enabled` is `true` by default in tests, so a suite that creates several of a limited
resource would start failing the moment a quota is seeded for it. Do not work around that by keeping
every test under the quota — the quota is an operational number that changes. Turning the flag off
does not stop usage from being recorded, only from being refused. Instead:

- Turn limits **off for the suite** at class level:
  `@SpringBootTest(..., properties = "recipai.limits.enabled=false")`. Existing tests then stay about
  the module's own behaviour.
- Put the limit tests in a `@Nested` class that turns them **on** with
  `@TestPropertySource(properties = "recipai.limits.enabled=true")`. The nested class gets its own
  context and container, and the enclosing instance's injected fields are wired from the *nested*
  context, so the outer suite's `restClient()` and creation helpers work unchanged inside it.
- **Set the nested class's own `limit_config` override** for its subject through `setLimitQuota`
  rather than relying on the shipped default, so the test does not break when an operator changes a
  production number. Delete the override in `@AfterEach`, and restore any resource *default* the test
  changed before it leaves.

```java
@Nested
@TestPropertySource(properties = "recipai.limits.enabled=true")
class LimitsEnforced { ... }
```

Teardown in such a class **deletes through the API, not through `limit_usage`**. Deleting over HTTP
exercises the real release path and leaves usage at zero as evidence it fired; a blanket
`DELETE FROM limit_usage` would erase exactly that evidence and hide a missed release — the one bug
these tests are the last line of defence against, since the outer suite runs with limits off. Only rows
fabricated for a subject with no API presence are deleted directly. Ending teardown with an assertion
that the subject's usage is back to zero attributes the failure to the test that broke release rather
than to the next one.

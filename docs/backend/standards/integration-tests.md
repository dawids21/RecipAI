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

### Reading Data the Test Has No Access To
When an assertion needs state the test cannot reach through the module's public surface, add a read method to that module's facade/service — do not reach around it with a hand-written SQL query. Seeding fixtures with `JdbcClient` is fine; *reading back* what the code under test did is not.

`LimitsFacade.standing(subject, resource)` exists for exactly this reason: `ExtractionIntegrationTest` and `LimitsIntegrationTest` assert the recorded standing through it instead of selecting from `limit_usage`.

```java
// Correct: read the standing through the facade
private int usedFor(String subject) {
    return limitsFacade.standing(subject, ExtractionService.EXTRACTION_RESOURCE)
            .map(LimitStanding::used)
            .orElse(0);
}

// Wrong: reach around the module into its tables
jdbcClient.sql("SELECT used FROM recipai.limit_usage WHERE ...").query(Integer.class).single();
```

Adding such a method widens a module's public API for a test's benefit, so **always tell the developer when you do it** — it is their call whether the method belongs there.

### Testing a Suite Whose Module Is Capped by `limits`

`recipai.limits.enabled` is `true` by default in tests, so a suite that creates several of a capped
resource would start failing the moment a cap is seeded for it. Do not work around that by keeping
every test under the cap — the cap is an operational number that changes. Instead:

- Turn limits **off for the suite** at class level:
  `@SpringBootTest(..., properties = "recipai.limits.enabled=false")`. Existing tests then stay about
  the module's own behaviour.
- Put the limit tests in a `@Nested` class that turns them **on** with
  `@TestPropertySource(properties = "recipai.limits.enabled=true")`. The nested class gets its own
  context and container, and the enclosing instance's injected fields are wired from the *nested*
  context, so the outer suite's `restClient()` and creation helpers work unchanged inside it.
- **Seed the nested class's own `limit_config` override** for its subject with `JdbcClient` rather than
  relying on the shipped default, so the test does not break when an operator changes a production
  number. Clean the override up in `@AfterEach` — it collides with
  `UNIQUE NULLS NOT DISTINCT (resource, subject)` on the next test.

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

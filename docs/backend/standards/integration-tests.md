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

`LimitsFacade.currentUsage(subject, resource)` exists for exactly this reason: `ExtractionIntegrationTest` and `LimitsIntegrationTest` assert the recorded standing through it instead of selecting from `limit_usage`.

```java
// Correct: read the standing through the facade
private int usedFor(String subject) {
    return limitsFacade.currentUsage(subject, ExtractionService.EXTRACTION_RESOURCE)
            .map(LimitUsageDetails::used)
            .orElse(0);
}

// Wrong: reach around the module into its tables
jdbcClient.sql("SELECT used FROM recipai.limit_usage WHERE ...").query(Integer.class).single();
```

Adding such a method widens a module's public API for a test's benefit, so **always tell the developer when you do it** — it is their call whether the method belongs there.

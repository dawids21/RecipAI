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
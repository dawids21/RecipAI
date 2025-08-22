## FEATURE:

I want to implement API authentication for my web application to ensure that only authorized users can access certain
endpoints.
The user will authenticate using a third-party OAuth2 provider (Google).
The authentication would use a token stored in a HTTP header for each request.
The app will be a resource server.
Use Spring Security and Spring OAuth2 Resource Server libraries (already added in pom.xml).
For now just secure the endpoints. No need to implement authorization logic.

## EXAMPLES:

### Integration Test

```java

@TestConfiguration
public class TestSecurityConfiguration {

    public static final String AUTH_TOKEN = "token";

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        JwtDecoder jwtDecoder = Mockito.mock(JwtDecoder.class);
        Jwt mockJwt = Jwt.withTokenValue(AUTH_TOKEN)
                .header("alg", "RS256")
                .claim("sub", "john.doe")
                .claim("email", "user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Mockito.when(jwtDecoder.decode(Mockito.anyString())).thenReturn(mockJwt);
        return jwtDecoder;
    }
}

@Import(TestSecurityConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTestsForOauth2ResourceServerApplicationTests {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", TestSecurityConfiguration.AUTH_TOKEN)
                .build();
    }

    @Test
    void shouldExtractRecipeFromText() throws Exception {
        String greeting = restClient()
                .get()
                .uri("/api/test")
                .retrieve()
                .body(String.class);

        System.out.println("greeting = " + greeting);
    }
}
```

## DOCUMENTATION:

- `docs/backend/api.md` - Update the API documentation to indicate which endpoints require authentication.
- `docs/backend/backend.md` - add authentication files to the codebase structure section

## OTHER CONSIDERATIONS:
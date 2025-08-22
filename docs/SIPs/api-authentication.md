# SIP: API Authentication with OAuth2 Resource Server

## Goal

- Implement API authentication for RecipAI backend using OAuth2 Resource Server with JWT tokens
- Configure authentication with Google as the third-party OAuth2 provider
- Secure all existing endpoints (/recipes, /extract) to require valid JWT tokens in Authorization header
- App will act as a resource server (not authorization server) - validates tokens issued by Google
- No authorization logic needed initially - just authentication (securing endpoints)
- Success criteria: All API endpoints return 401 Unauthorized without valid JWT token, and 200/201 with valid token

## Context

### Documentation and References

- **Spring Security OAuth2 Resource Server
  **: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- **OAuth2 Resource Server with Spring Security**: https://www.baeldung.com/spring-security-oauth-resource-server
- **Google OAuth2 Configuration**: https://accounts.google.com/.well-known/openid_configuration
- **Example Integration Test Pattern**: Feature file shows TestSecurityConfiguration with mocked JwtDecoder for
  RestClient tests
- **Current Exception Handling**: `GlobalExceptionHandler` in recipes module for centralized error handling
- **Existing Controller Patterns**: `RecipeController` and `ExtractionController` use `@RestController`,
  `@RequestMapping`, Lombok annotations

### Current Codebase Tree

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── extraction/                      # "extraction" module  
│   │   ├── ExtractionController.java    # POST /extract/text
│   │   ├── ExtractionService.java
│   │   ├── ExtractTextRequest.java
│   │   └── ExtractedRecipe.java
│   └── recipes/                         # "recipes" module
│       ├── RecipeController.java        # GET/POST/PUT/DELETE /recipes
│       ├── RecipeService.java
│       ├── GlobalExceptionHandler.java  # Centralized exception handling
│       ├── ErrorResponse.java
│       └── [other recipe DTOs and entities]
├── src/main/resources/
│   └── application.yml                  # Spring Boot configuration
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers
    ├── TestcontainersConfiguration.class
    ├── extraction/ExtractionIntegrationTest.java
    └── recipes/RecipeIntegrationTest.java
```

### Desired Codebase Tree

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── security/                        # NEW: "security" module
│   │   └── SecurityConfig.java          # OAuth2 Resource Server configuration
│   ├── extraction/                      # "extraction" module (unchanged)
│   └── recipes/                         # "recipes" module (unchanged)
├── src/main/resources/
│   └── application.yml                  # Updated with OAuth2 configuration
└── src/test/java/xyz/stasiak/recipai/   # Updated integration tests
    ├── TestcontainersConfiguration.class
    ├── TestSecurityConfiguration.java   # NEW: Test configuration with mocked JWT decoder
    ├── extraction/ExtractionIntegrationTest.java
    └── recipes/RecipeIntegrationTest.java
```

### Known Gotchas of Our Codebase and Library Quirks

- **Google Dual Issuer Format**: Google may return either "iss": "https://accounts.google.com" or "iss": "
  accounts.google.com" - Spring's JwtIssuerValidator validates against only one value
- **Spring Boot 3.5.4**: Uses newer SecurityFilterChain bean approach, not WebSecurityConfigurerAdapter
- **Dependencies Already Present**: pom.xml already contains spring-boot-starter-security,
  spring-boot-starter-oauth2-resource-server, spring-security-test
- **Package Structure**: Modules organized by feature (recipes, extraction), security should follow same pattern
- **Test Pattern**: Uses @Import(TestcontainersConfiguration.class) and RestClient for integration testing
- **Lombok Usage**: Controllers use @RequiredArgsConstructor, @Slf4j - security config should follow same pattern

## Implementation Plan

### Tasks

```
Task 1: Create Security Configuration
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/security/SecurityConfig.java
  Changes:
    - [ ] Create @Configuration @EnableWebSecurity class following codebase patterns
    - [ ] Define SecurityFilterChain bean with oauth2ResourceServer() configuration
    - [ ] Use authorizeHttpRequests() to require authentication for /recipes/** and /extract/**
    - [ ] Allow actuator endpoints (if any) to be accessible without authentication
    - [ ] Follow existing controller patterns: package-private class, @RequiredArgsConstructor, @Slf4j

Task 2: Configure OAuth2 Resource Server Properties
  Action: MODIFY
  File: backend/src/main/resources/application.yml
  Changes:
    - [ ] Add spring.security.oauth2.resourceserver.jwt.issuer-uri property for Google
    - [ ] Add spring.security.oauth2.resourceserver.jwt.jwk-set-uri property for Google certs
    - [ ] Configure to handle Google's dual issuer format if needed
    - [ ] Ensure no conflicts with existing application properties

Task 3: Create Test Security Configuration
  Action: CREATE  
  File: backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java
  Changes:
    - [ ] Create @TestConfiguration class following feature file example exactly
    - [ ] Define @Bean @Primary JwtDecoder that returns mocked JWT
    - [ ] Mock JWT with required claims: "sub", "email", "iss", "aud" 
    - [ ] Make AUTH_TOKEN constant accessible for tests
    - [ ] Place in test root directory like TestcontainersConfiguration

Task 4: Update Recipe Integration Tests
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Add @Import(TestSecurityConfiguration.class) to import security test config
    - [ ] Modify restClient() method to include Authorization header with TestSecurityConfiguration.AUTH_TOKEN
    - [ ] Use defaultHeader("Authorization", "Bearer " + TestSecurityConfiguration.AUTH_TOKEN)
    - [ ] Ensure all existing test scenarios continue to pass

Task 5: Update Extraction Integration Tests  
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java
  Changes:
    - [ ] Add @Import(TestSecurityConfiguration.class) to import security test config
    - [ ] Modify restClient() method to include Authorization header with TestSecurityConfiguration.AUTH_TOKEN
    - [ ] Use defaultHeader("Authorization", "Bearer " + TestSecurityConfiguration.AUTH_TOKEN)
    - [ ] Ensure existing shouldExtractRecipeFromText() test continues to pass

Task 6: Update Documentation
  Action: MODIFY
  File: docs/backend/api.md
  Changes:
    - [ ] Add Authentication section describing OAuth2 JWT requirement
    - [ ] Document Authorization header format: "Bearer <jwt-token>"
    - [ ] Mark all endpoints as requiring authentication
    - [ ] Add example of 401 response for missing/invalid tokens

Task 7: Update Backend Documentation
  Action: MODIFY  
  File: docs/backend/backend.md
  Changes:
    - [ ] Add "security" module to codebase structure
    - [ ] Document SecurityConfig.java in security module
    - [ ] Update modules section to include security functionality
```

### Per Task Pseudocode

```java
// Task 1: SecurityConfig.java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/recipes/**", "/extract/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            )
            .build();
    }
}

// Task 3: TestSecurityConfiguration.java  
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
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
mvn compile

# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
mvn test -Dtest="*IntegrationTest"
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points

- **API Changes**: All existing endpoints now require Authorization header with valid JWT token
- **Client Impact**: Mobile app and any API clients must obtain Google OAuth2 tokens and include in requests
- **Database**: No database schema changes required
- **External Dependencies**: Integration with Google OAuth2 for token validation via jwk-set-uri

## Documentation

- **docs/backend/api.md**: Add authentication requirements and examples to all endpoint documentation
- **docs/backend/backend.md**: Add security module to codebase structure section

## Final Validation Checklist

- [ ] Correct syntax - mvn compile passes
- [ ] Correct style - follows existing codebase patterns
- [ ] All tests pass - mvn test succeeds
- [ ] Manual test successful - can access endpoints with valid token, get 401 without token
- [ ] Error cases handled gracefully - proper 401 responses for missing/invalid tokens
- [ ] Logs are informative but not verbose - debug level logging for security events
- [ ] Documentation updated - API docs reflect authentication requirements

## SIP Quality Score: 9/10

**Confidence Level**: Very High - This SIP provides comprehensive context including:

- ✅ Detailed analysis of existing codebase patterns and structure
- ✅ Specific Spring Security OAuth2 Resource Server documentation and best practices
- ✅ Complete testing strategy with mocked JWT decoder following provided example
- ✅ Step-by-step implementation tasks with clear file modifications
- ✅ Validation commands specific to Java/Maven projects
- ✅ Integration considerations and documentation updates
- ✅ Addresses Google OAuth2 specific considerations (dual issuer format)
- ✅ Uses RestClient testing approach consistent with existing test patterns

**Minor Risk**: Google's dual issuer format may require additional custom validation configuration if default Spring
Security validation fails. This can be addressed during implementation by testing with actual Google tokens.
package xyz.stasiak.recipai;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

@TestConfiguration
public class TestSecurityConfiguration {

    public static final String AUTH_TOKEN = "test-jwt-token";
    public static final String AUTH_TOKEN_USER_1 = "test-jwt-token-user1";
    public static final String AUTH_TOKEN_USER_2 = "test-jwt-token-user2";

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        JwtDecoder jwtDecoder = Mockito.mock(JwtDecoder.class);

        Jwt mockJwt = Jwt.withTokenValue(AUTH_TOKEN)
                .header("alg", "RS256")
                .claim("sub", "john.doe")
                .claim("email", "user@example.com")
                .claim("iss", "https://accounts.google.com")
                .claim("aud", "test-audience")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // User 1 token
        Jwt mockJwtUser1 = Jwt.withTokenValue(AUTH_TOKEN_USER_1)
                .header("alg", "RS256")
                .claim("sub", "user1")
                .claim("email", "user1@example.com")
                .claim("iss", "https://accounts.google.com")
                .claim("aud", "test-audience")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // User 2 token
        Jwt mockJwtUser2 = Jwt.withTokenValue(AUTH_TOKEN_USER_2)
                .header("alg", "RS256")
                .claim("sub", "user2")
                .claim("email", "user2@example.com")
                .claim("iss", "https://accounts.google.com")
                .claim("aud", "test-audience")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Configure mock to return appropriate JWT based on token
        Mockito.when(jwtDecoder.decode(AUTH_TOKEN)).thenReturn(mockJwt);
        Mockito.when(jwtDecoder.decode(AUTH_TOKEN_USER_1)).thenReturn(mockJwtUser1);
        Mockito.when(jwtDecoder.decode(AUTH_TOKEN_USER_2)).thenReturn(mockJwtUser2);
        
        return jwtDecoder;
    }
}
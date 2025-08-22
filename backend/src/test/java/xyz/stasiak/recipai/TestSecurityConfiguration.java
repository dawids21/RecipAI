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
        Mockito.when(jwtDecoder.decode(Mockito.anyString())).thenReturn(mockJwt);
        return jwtDecoder;
    }
}
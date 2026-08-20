package xyz.stasiak.recipai.config.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Configuration
@Profile("dev")
@Slf4j
class DevAuthConfig {

    @PostConstruct
    void warnBypassEnabled() {
        log.warn("AUTHENTICATION BYPASS ENABLED (dev profile) - every bearer token is accepted as the caller's email");
    }

    @Bean
    JwtDecoder devJwtDecoder(
            Clock clock,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        return token -> {
            Instant issuedAt = clock.instant();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("email", token)
                    .claim("email_verified", true)
                    .issuer(issuerUri)
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plus(Duration.ofHours(1)))
                    .build();
        };
    }
}

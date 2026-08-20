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

    /**
     * Appended to the bearer token to form the caller's email. RFC 6750 forbids '@' in a
     * bearer token, so the token cannot itself be an email - but share/unshare requests are
     * validated with @Email, so the identity must be one. Suffixing reconciles the two:
     * "Bearer alice" is alice@local.test, which is a legal share target and a legal caller.
     * '.test' is reserved by RFC 2606 and can never resolve to a real address.
     */
    private static final String DEV_EMAIL_DOMAIN = "@local.test";

    @PostConstruct
    void warnBypassEnabled() {
        log.warn("AUTHENTICATION BYPASS ENABLED (dev profile) - every bearer token is accepted as a caller, "
                + "with '{}' appended as the email (e.g. 'Bearer alice' is alice{})", DEV_EMAIL_DOMAIN, DEV_EMAIL_DOMAIN);
    }

    @Bean
    JwtDecoder devJwtDecoder(
            Clock clock,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        return token -> {
            Instant issuedAt = clock.instant();
            String email = token + DEV_EMAIL_DOMAIN;
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(email)
                    .claim("email", email)
                    .claim("email_verified", true)
                    .issuer(issuerUri)
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plus(Duration.ofHours(1)))
                    .build();
        };
    }
}

package xyz.stasiak.recipai.config.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthoritiesConverterTest {

    private static final AdminProperties ALLOWLIST = new AdminProperties(Set.of("admin@example.com"));

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
    }

    private static Jwt verifiedToken(String email) {
        return token().claim("email", email).claim("email_verified", true).build();
    }

    @Test
    void shouldGrantAdminWhenEmailIsAllowlistedAndVerified() {
        AdminAuthoritiesConverter converter = new AdminAuthoritiesConverter(ALLOWLIST);

        Collection<GrantedAuthority> authorities = converter.convert(verifiedToken("admin@example.com"));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void shouldNotGrantAdminWhenEmailIsNotVerified(Boolean emailVerified) {
        AdminAuthoritiesConverter converter = new AdminAuthoritiesConverter(ALLOWLIST);
        Jwt.Builder builder = token().claim("email", "admin@example.com");
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }

        Collection<GrantedAuthority> authorities = converter.convert(builder.build());

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldNotGrantAdminWhenEmailIsNotAllowlisted() {
        AdminAuthoritiesConverter converter = new AdminAuthoritiesConverter(ALLOWLIST);

        Collection<GrantedAuthority> authorities = converter.convert(verifiedToken("other@example.com"));

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldNotGrantAdminWhenEmailClaimIsMissing() {
        AdminAuthoritiesConverter converter = new AdminAuthoritiesConverter(ALLOWLIST);

        Collection<GrantedAuthority> authorities = converter.convert(token().claim("email_verified", true).build());

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldGrantAdminWhenAllowlistDiffersOnlyInCase() {
        AdminAuthoritiesConverter mixedCaseAllowlist =
                new AdminAuthoritiesConverter(new AdminProperties(Set.of("Admin@Example.COM")));
        AdminAuthoritiesConverter lowercaseAllowlist = new AdminAuthoritiesConverter(ALLOWLIST);

        Collection<GrantedAuthority> fromMixedAllowlist = mixedCaseAllowlist.convert(verifiedToken("admin@example.com"));
        Collection<GrantedAuthority> fromMixedToken = lowercaseAllowlist.convert(verifiedToken("ADMIN@example.Com"));

        assertThat(fromMixedAllowlist).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(fromMixedToken).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void shouldNotGrantAdminWhenAllowlistIsEmpty() {
        AdminAuthoritiesConverter converter = new AdminAuthoritiesConverter(new AdminProperties(Set.of()));

        Collection<GrantedAuthority> authorities = converter.convert(verifiedToken("admin@example.com"));

        assertThat(authorities).isEmpty();
    }
}

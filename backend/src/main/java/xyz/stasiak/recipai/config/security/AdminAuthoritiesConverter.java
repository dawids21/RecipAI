package xyz.stasiak.recipai.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

class AdminAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    static final String ADMIN_ROLE = "ADMIN";

    private final AdminProperties properties;

    AdminAuthoritiesConverter(AdminProperties properties) {
        this.properties = properties;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        if (email == null || !Boolean.TRUE.equals(verified)) {
            return List.of();
        }
        if (properties.adminEmails().contains(email.toLowerCase(Locale.ROOT))) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + ADMIN_ROLE));
        }
        return List.of();
    }
}

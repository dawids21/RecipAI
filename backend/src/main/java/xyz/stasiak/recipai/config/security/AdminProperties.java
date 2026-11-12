package xyz.stasiak.recipai.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "recipai.security")
record AdminProperties(@DefaultValue Set<String> adminEmails) {

    AdminProperties {
        adminEmails = adminEmails.stream()
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}

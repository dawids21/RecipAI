package xyz.stasiak.recipai.limits;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "recipai.limits")
record LimitsProperties(
        @DefaultValue("true") boolean enabled
) {
}

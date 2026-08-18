package xyz.stasiak.recipai.limits;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LimitsFacade {

    private final LimitService limitService;
    private final LimitsProperties limitsProperties;

    @PostConstruct
    void warnWhenDisabled() {
        if (!limitsProperties.enabled()) {
            log.warn("Usage limits are DISABLED (recipai.limits.enabled=false) - no reservations will be recorded");
        }
    }

    public void reserve(String subject, String resource) {
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, skipping reservation of resource: {} for subject: {}", resource, subject);
            return;
        }
        log.debug("Reserving resource: {} for subject: {}", resource, subject);
        limitService.reserve(subject, resource);
    }

    public Optional<LimitUsageDetails> currentUsage(String subject, String resource) {
        log.debug("Getting current usage of resource: {} for subject: {}", resource, subject);
        return limitService.currentUsage(subject, resource);
    }
}

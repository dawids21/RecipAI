package xyz.stasiak.recipai.limits;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
            log.warn("Usage limits are DISABLED (recipai.limits.enabled=false) - usage is still recorded, but nothing is refused");
        }
    }

    public void reserve(String subject, String resource) {
        reserve(subject, subject, resource);
    }

    public void reserve(String configSubject, String usageSubject, String resource) {
        log.debug("Reserving resource: {} for subject: {} (configured by: {})", resource, usageSubject, configSubject);
        limitService.reserve(configSubject, usageSubject, resource);
    }

    public void release(String subject, String resource) {
        release(subject, subject, resource);
    }

    public void release(String configSubject, String usageSubject, String resource) {
        log.debug("Releasing resource: {} for subject: {} (configured by: {})", resource, usageSubject, configSubject);
        limitService.release(configSubject, usageSubject, resource);
    }

    public void clear(String subject, String resource) {
        log.debug("Clearing resource: {} for subject: {}", resource, subject);
        limitService.clear(subject, resource);
    }

    public Optional<LimitBalance> getBalance(String subject, String resource) {
        log.debug("Getting balance of resource: {} for subject: {}", resource, subject);
        return limitService.getBalance(subject, resource);
    }

    public List<LimitQuota> getQuotas(String subject) {
        log.debug("Getting quotas for subject: {}", subject);
        return limitService.getQuotas(subject);
    }

    public Optional<LimitQuota> getQuota(String subject, String resource) {
        log.debug("Getting quota of resource: {} for subject: {}", resource, subject);
        return limitService.getQuota(subject, resource);
    }
}

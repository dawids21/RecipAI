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
            log.warn("Usage limits are DISABLED (recipai.limits.enabled=false) - no reservations will be recorded");
        }
    }

    public void reserve(String subject, String resource) {
        reserve(subject, subject, resource);
    }

    public void reserve(String configSubject, String usageSubject, String resource) {
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, skipping reservation of resource: {} for subject: {}", resource, usageSubject);
            return;
        }
        log.debug("Reserving resource: {} for subject: {} (configured by: {})", resource, usageSubject, configSubject);
        limitService.reserve(configSubject, usageSubject, resource);
    }

    public void release(String subject, String resource) {
        release(subject, subject, resource);
    }

    public void release(String configSubject, String usageSubject, String resource) {
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, skipping release of resource: {} for subject: {}", resource, usageSubject);
            return;
        }
        log.debug("Releasing resource: {} for subject: {} (configured by: {})", resource, usageSubject, configSubject);
        limitService.release(configSubject, usageSubject, resource);
    }

    public void clear(String subject, String resource) {
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, skipping clear of resource: {} for subject: {}", resource, subject);
            return;
        }
        log.debug("Clearing resource: {} for subject: {}", resource, subject);
        limitService.clear(subject, resource);
    }

    public Optional<LimitStanding> standing(String subject, String resource) {
        log.debug("Getting standing of resource: {} for subject: {}", resource, subject);
        return limitService.standing(subject, resource);
    }

    public List<LimitCap> caps(String subject) {
        log.debug("Getting caps for subject: {}", subject);
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, returning no caps for subject: {}", subject);
            return List.of();
        }
        return limitService.caps(subject);
    }

    public Optional<LimitCap> cap(String subject, String resource) {
        log.debug("Getting cap of resource: {} for subject: {}", resource, subject);
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, returning no cap of resource: {} for subject: {}", resource, subject);
            return Optional.empty();
        }
        return limitService.cap(subject, resource);
    }
}

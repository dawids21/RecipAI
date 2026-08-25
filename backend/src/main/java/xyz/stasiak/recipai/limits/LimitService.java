package xyz.stasiak.recipai.limits;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class LimitService {

    /**
     * The maximum reserved against while limits are disabled: usage is still recorded, but the
     * statement can never refuse.
     */
    private static final int UNLIMITED = Integer.MAX_VALUE;

    private final LimitConfigRepository limitConfigRepository;
    private final LimitUsageRepository limitUsageRepository;
    private final LimitsProperties limitsProperties;
    private final Clock clock;

    @Transactional
    void reserve(String configSubject, String usageSubject, String resource) {
        // The kill-switch suppresses refusals, not misconfiguration: an unconfigured resource is a bug
        // in either mode, and one the dev profile would otherwise never surface.
        LimitConfig config = limitConfigRepository.resolve(resource, configSubject)
                .orElseThrow(() -> {
                    log.error("No limit configuration found for resource: {}", resource);
                    return new LimitConfigurationMissingException(resource);
                });

        Instant now = clock.instant();
        int max = limitsProperties.enabled() ? config.getMaxValue() : UNLIMITED;

        int granted = limitUsageRepository.reserve(resource, usageSubject, now, config.cutoffFrom(now), max);
        if (granted == 1) {
            return;
        }

        // A maximum of zero refuses before any usage row exists, so the balance is not always stored.
        Optional<LimitUsage> usage = limitUsageRepository.findById(new LimitUsageId(resource, usageSubject));
        int used = usage.map(LimitUsage::getUsed).orElse(0);
        Long retryAfterSeconds = usage.map(row -> config.resetsInSeconds(row.getPeriodStart(), now)).orElse(null);

        log.warn("Limit exceeded for resource: {}, subject: {}, used: {}, limit: {}",
                resource, usageSubject, used, config.getMaxValue());
        throw new LimitExceededException(resource, config.getKind(), config.getMaxValue(), used, retryAfterSeconds);
    }

    @Transactional
    void release(String configSubject, String usageSubject, String resource) {
        Optional<LimitConfig> config = limitConfigRepository.resolve(resource, configSubject);
        if (config.isEmpty()) {
            log.error("No limit configuration found for resource: {}", resource);
            return;
        }

        if (!config.get().refundsOnRelease()) {
            return;
        }

        limitUsageRepository.release(resource, usageSubject);
    }

    @Transactional
    void clear(String subject, String resource) {
        int cleared = limitUsageRepository.clear(resource, subject);
        log.debug("Cleared usage of resource: {} for subject: {} (rows: {})", resource, subject, cleared);
    }

    @Transactional(readOnly = true)
    Optional<LimitBalance> getBalance(String subject, String resource) {
        Optional<LimitUsage> usage = limitUsageRepository.findById(new LimitUsageId(resource, subject));
        if (usage.isEmpty()) {
            return Optional.empty();
        }

        Optional<LimitConfig> config = limitConfigRepository.resolve(resource, subject);
        Instant now = clock.instant();
        Instant periodStart = usage.get().getPeriodStart();

        if (config.filter(c -> c.hasPassed(periodStart, now)).isPresent()) {
            return Optional.of(LimitBalance.zero());
        }

        return Optional.of(usage.get().toBalance(
                config.map(c -> c.resetsInSeconds(periodStart, now)).orElse(null)));
    }

    @Transactional(readOnly = true)
    List<LimitQuota> getQuotas(String subject) {
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, returning no quotas for subject: {}", subject);
            return List.of();
        }
        return limitConfigRepository.resolveAll(subject).stream()
                .map(LimitConfig::toQuota)
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<LimitQuota> getQuota(String subject, String resource) {
        if (!limitsProperties.enabled()) {
            log.debug("Limits disabled, returning no quota of resource: {} for subject: {}", resource, subject);
            return Optional.empty();
        }
        return limitConfigRepository.resolve(resource, subject)
                .map(LimitConfig::toQuota);
    }
}

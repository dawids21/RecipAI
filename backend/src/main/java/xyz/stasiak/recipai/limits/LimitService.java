package xyz.stasiak.recipai.limits;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class LimitService {

    private final LimitConfigRepository limitConfigRepository;
    private final LimitUsageRepository limitUsageRepository;
    private final Clock clock;

    @Transactional
    void reserve(String configSubject, String usageSubject, String resource) {
        LimitConfig config = limitConfigRepository.resolve(resource, configSubject)
                .orElseThrow(() -> {
                    log.error("No limit configuration found for resource: {}", resource);
                    return new LimitConfigurationMissingException(resource);
                });

        Instant now = clock.instant();
        Instant cutoff = config.getPeriod() == null ? Instant.EPOCH : config.getPeriod().cutoffFrom(now);

        int granted = limitUsageRepository.reserve(resource, usageSubject, now, cutoff, config.getMaxValue());
        if (granted == 1) {
            return;
        }

        // A maximum of zero refuses before any usage row exists, so the standing is not always stored.
        Optional<LimitUsage> usage = limitUsageRepository.findById(new LimitUsageId(resource, usageSubject));
        int used = usage.map(row -> row.getUsed()).orElse(0);

        Long retryAfterSeconds = null;
        if (config.getKind() == LimitKind.FLOW && config.getPeriod() != null && usage.isPresent()) {
            Instant nextStart = config.getPeriod().nextStart(usage.get().getPeriodStart());
            retryAfterSeconds = Math.max(1, Duration.between(now, nextStart).getSeconds());
        }

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

        if (config.get().getKind() == LimitKind.FLOW) {
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
    Optional<LimitStanding> standing(String subject, String resource) {
        Optional<LimitUsage> usage = limitUsageRepository.findById(new LimitUsageId(resource, subject));
        if (usage.isEmpty()) {
            return Optional.empty();
        }

        Optional<LimitConfig> config = limitConfigRepository.resolve(resource, subject);
        Instant now = clock.instant();

        boolean lapsed = config.isPresent() && config.get().getPeriod() != null
                && !usage.get().getPeriodStart().isAfter(config.get().getPeriod().cutoffFrom(now));
        if (lapsed) {
            return Optional.of(new LimitStanding(0, null, null));
        }

        Long resetsInSeconds = null;
        if (config.isPresent() && config.get().getKind() == LimitKind.FLOW && config.get().getPeriod() != null) {
            Instant nextStart = config.get().getPeriod().nextStart(usage.get().getPeriodStart());
            resetsInSeconds = Math.max(1, Duration.between(now, nextStart).getSeconds());
        }

        return Optional.of(new LimitStanding(usage.get().getUsed(), usage.get().getPeriodStart(), resetsInSeconds));
    }

    @Transactional(readOnly = true)
    List<LimitCap> caps(String subject) {
        return limitConfigRepository.resolveAll(subject).stream()
                .map(config -> new LimitCap(config.getResource(), config.getKind(), config.getMaxValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<LimitCap> cap(String subject, String resource) {
        return limitConfigRepository.resolve(resource, subject)
                .map(config -> new LimitCap(config.getResource(), config.getKind(), config.getMaxValue()));
    }
}

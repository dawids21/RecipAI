package xyz.stasiak.recipai.limits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "recipai.limits.enabled=true")
class LimitsIntegrationTest {

    @Autowired
    private LimitsFacade limitsFacade;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM recipai.limit_usage WHERE resource LIKE 'TEST_LIMIT_%'").update();
        jdbcClient.sql("DELETE FROM recipai.limit_config WHERE resource LIKE 'TEST_LIMIT_%'").update();
    }

    @Test
    void shouldGrantAndInsertUsageRowOnFirstReserve() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);

        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void shouldIncrementUsedOnEachSubsequentGrantWhileUnderLimit() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldRefuseOnceUsedEqualsMaxAndNotAdvancePastIt() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldPreferSubjectOverrideOverDefaultWhenLower() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);
        seedConfig(resource, subject, "FLOW", 1, null);

        limitsFacade.reserve(subject, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldPreferSubjectOverrideOverDefaultWhenHigher() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 1, null);
        seedConfig(resource, subject, "FLOW", 3, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldFallBackToResourceDefaultWhenSubjectHasNoOverride() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldAdmitNextReserveWithNoRestartAfterRaisingMaxValueBySql() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);

        updateMaxValue(resource, null, 5);

        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldNeverRestartStockConfigurationEvenWithOldPeriodStart() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 3, null);
        seedUsage(resource, subject, 3, Instant.now().minus(Duration.ofDays(365)));

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldNeverRestartFlowWithNoPeriod() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, null);
        seedUsage(resource, subject, 2, Instant.now().minus(Duration.ofDays(365)));

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldRestartFlowDayLazilyWhenPeriodStartTwoDaysOld() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, "DAY");
        Instant oldStart = Instant.now().minus(Duration.ofDays(2));
        seedUsage(resource, subject, 2, oldStart);

        limitsFacade.reserve(subject, resource);

        LimitStanding usage = limitsFacade.standing(subject, resource).orElseThrow();
        assertThat(usage.used()).isEqualTo(1);
        assertThat(usage.periodStart()).isAfter(oldStart.plus(Duration.ofDays(1)));
    }

    @Test
    void shouldNotRestartFlowDayWhenPeriodStartInsideWindow() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, "DAY");
        Instant recentStart = Instant.now().minus(Duration.ofHours(1));
        seedUsage(resource, subject, 2, recentStart);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldCarryResourceKindLimitAndUsedOnRefusal() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 1, null);

        limitsFacade.reserve(subject, resource);

        LimitExceededException ex = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject, resource));

        assertThat(ex.resource()).isEqualTo(resource);
        assertThat(ex.kind()).isEqualTo(LimitKind.FLOW);
        assertThat(ex.limit()).isEqualTo(1);
        assertThat(ex.used()).isEqualTo(1);
    }

    @Test
    void shouldCarryPositiveRetryAfterForFlowWithPeriodAndNullOtherwise() {
        String resourceFlowDay = newResource();
        String subject1 = newSubject();
        seedConfig(resourceFlowDay, null, "FLOW", 1, "DAY");
        limitsFacade.reserve(subject1, resourceFlowDay);
        LimitExceededException flowEx = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject1, resourceFlowDay));
        assertThat(flowEx.retryAfterSeconds()).isPositive();

        String resourceStock = newResource();
        String subject2 = newSubject();
        seedConfig(resourceStock, null, "STOCK", 1, null);
        limitsFacade.reserve(subject2, resourceStock);
        LimitExceededException stockEx = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject2, resourceStock));
        assertThat(stockEx.retryAfterSeconds()).isNull();

        String resourceFlowNoPeriod = newResource();
        String subject3 = newSubject();
        seedConfig(resourceFlowNoPeriod, null, "FLOW", 1, null);
        limitsFacade.reserve(subject3, resourceFlowNoPeriod);
        LimitExceededException noPeriodEx = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject3, resourceFlowNoPeriod));
        assertThat(noPeriodEx.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldRefuseFirstReserveWhenMaxIsZero() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 0, null);

        LimitExceededException ex = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject, resource));

        assertThat(ex).isNotNull();
        assertThat(ex.limit()).isZero();
        assertThat(ex.used()).isZero();
        assertThat(ex.retryAfterSeconds()).isNull();
        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldRefuseWhenMaxIsLoweredToZeroBelowExistingUsage() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 2, "DAY");

        limitsFacade.reserve(subject, resource);
        updateMaxValue(resource, null, 0);

        LimitExceededException ex = catchThrowableOfType(LimitExceededException.class,
                () -> limitsFacade.reserve(subject, resource));

        assertThat(ex).isNotNull();
        assertThat(ex.limit()).isZero();
        assertThat(ex.used()).isEqualTo(1);
        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void shouldNotRestartElapsedPeriodWhenMaxIsZero() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 0, "DAY");
        Instant oldStart = Instant.now().minus(Duration.ofDays(2));
        seedUsage(resource, subject, 2, oldStart);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isZero();
    }

    @Test
    void shouldThrowLimitConfigurationMissingExceptionWhenNoConfigExists() {
        String resource = newResource();
        String subject = newSubject();

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitConfigurationMissingException.class);
    }

    @Test
    void shouldTreatTwoSubjectsOnSameResourceIndependently() {
        String resource = newResource();
        String subjectA = newSubject();
        String subjectB = newSubject();
        seedConfig(resource, null, "FLOW", 1, null);

        limitsFacade.reserve(subjectA, resource);
        limitsFacade.reserve(subjectB, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subjectA, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> limitsFacade.reserve(subjectB, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldTreatOneSubjectOnTwoResourcesIndependently() {
        String resourceA = newResource();
        String resourceB = newResource();
        String subject = newSubject();
        seedConfig(resourceA, null, "FLOW", 1, null);
        seedConfig(resourceB, null, "FLOW", 1, null);

        limitsFacade.reserve(subject, resourceA);
        limitsFacade.reserve(subject, resourceB);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resourceA))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> limitsFacade.reserve(subject, resourceB))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldGrantExactlyMaxUnderConcurrentReserves() throws Exception {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    limitsFacade.reserve(subject, resource);
                    granted.incrementAndGet();
                } catch (LimitExceededException e) {
                    refused.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(granted.get()).isEqualTo(5);
        assertThat(refused.get()).isEqualTo(11);
        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(5);
    }

    @Test
    void shouldDecrementUsedByOneForStockConfiguredSubject() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedUsage(resource, subject, 3, Instant.now());

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldLeaveUsedUnchangedForFlowConfiguredSubject() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);
        seedUsage(resource, subject, 3, Instant.now());

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldLeaveUsedUnchangedWhenSubjectFlowOverrideShadowsStockDefault() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedConfig(resource, subject, "FLOW", 5, null);
        seedUsage(resource, subject, 3, Instant.now());

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldFloorAtZeroWhenReleasingTwiceFromUsedOne() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedUsage(resource, subject, 1, Instant.now());

        limitsFacade.release(subject, resource);
        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(0);
    }

    @Test
    void shouldCreateNoRowWhenReleasingWithNoUsageRowPresent() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldReturnSilentlyWhenNoConfigurationResolvesAtAllOnRelease() {
        String resource = newResource();
        String subject = newSubject();

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldAdmitReserveRefusedAtCapAfterOneRelease() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 1, null);

        limitsFacade.reserve(subject, resource);
        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);

        limitsFacade.release(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void shouldLeaveUsedAtExactlyZeroUnderConcurrentReleases() throws Exception {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedUsage(resource, subject, 1, Instant.now());

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    limitsFacade.release(subject, resource);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(0);
    }

    @Test
    void shouldResolveConfigurationFromConfigSubjectAndCountAgainstUsageSubject() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubject = newSubject();
        seedConfig(resource, null, "FLOW", 1, null);
        seedConfig(resource, configSubject, "FLOW", 3, null);

        limitsFacade.reserve(configSubject, usageSubject, resource);
        limitsFacade.reserve(configSubject, usageSubject, resource);
        limitsFacade.reserve(configSubject, usageSubject, resource);

        assertThat(limitsFacade.standing(usageSubject, resource).orElseThrow().used()).isEqualTo(3);
        assertThat(limitsFacade.standing(configSubject, resource)).isEmpty();
    }

    @Test
    void shouldCountTwoUsageSubjectsIndependentlyUnderOneConfigSubject() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubjectA = newSubject();
        String usageSubjectB = newSubject();
        seedConfig(resource, configSubject, "FLOW", 1, null);

        limitsFacade.reserve(configSubject, usageSubjectA, resource);
        limitsFacade.reserve(configSubject, usageSubjectB, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(configSubject, usageSubjectA, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> limitsFacade.reserve(configSubject, usageSubjectB, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldApplyConfigSubjectOverrideToEveryUsageSubjectResolvingThroughIt() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubjectA = newSubject();
        String usageSubjectB = newSubject();
        seedConfig(resource, configSubject, "FLOW", 1, null);

        limitsFacade.reserve(configSubject, usageSubjectA, resource);
        limitsFacade.reserve(configSubject, usageSubjectB, resource);
        assertThatThrownBy(() -> limitsFacade.reserve(configSubject, usageSubjectA, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> limitsFacade.reserve(configSubject, usageSubjectB, resource))
                .isInstanceOf(LimitExceededException.class);

        updateMaxValue(resource, configSubject, 2);

        limitsFacade.reserve(configSubject, usageSubjectA, resource);
        limitsFacade.reserve(configSubject, usageSubjectB, resource);

        assertThat(limitsFacade.standing(usageSubjectA, resource).orElseThrow().used()).isEqualTo(2);
        assertThat(limitsFacade.standing(usageSubjectB, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldRefuseWithConfigSubjectLimitAndUsageSubjectUsed() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubject = newSubject();
        seedConfig(resource, configSubject, "FLOW", 1, null);

        limitsFacade.reserve(configSubject, usageSubject, resource);

        LimitExceededException ex = catchThrowableOfType(LimitExceededException.class,
                () -> limitsFacade.reserve(configSubject, usageSubject, resource));

        assertThat(ex.limit()).isEqualTo(1);
        assertThat(ex.used()).isEqualTo(1);
    }

    @Test
    void shouldFollowConfigSubjectKindOnTwoSubjectRelease() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubject = newSubject();
        seedConfig(resource, configSubject, "FLOW", 5, null);
        seedUsage(resource, usageSubject, 3, Instant.now());

        limitsFacade.release(configSubject, usageSubject, resource);
        assertThat(limitsFacade.standing(usageSubject, resource).orElseThrow().used()).isEqualTo(3);

        jdbcClient.sql("UPDATE recipai.limit_config SET kind = 'STOCK' WHERE resource = :resource AND subject = :subject")
                .param("resource", resource)
                .param("subject", configSubject)
                .update();

        limitsFacade.release(configSubject, usageSubject, resource);
        assertThat(limitsFacade.standing(usageSubject, resource).orElseThrow().used()).isEqualTo(2);
        assertThat(limitsFacade.standing(configSubject, resource)).isEmpty();
    }

    @Test
    void shouldBehaveIdenticallyForTwoArgumentAndThreeArgumentFormsWithEqualSubjects() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);

        limitsFacade.reserve(subject, subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(2);

        limitsFacade.release(subject, subject, resource);
        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(0);
    }

    @Test
    void shouldDeleteUsageRowOnClear() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedUsage(resource, subject, 3, Instant.now());

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldDoNothingWhenClearingAbsentSubject() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldClearFlowConfiguredSubjectToo() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);
        seedUsage(resource, subject, 3, Instant.now());

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldClearWithNoConfigurationAtAll() {
        String resource = newResource();
        String subject = newSubject();
        seedUsage(resource, subject, 3, Instant.now());

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStandingWhenNoUsageRowExists() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);

        assertThat(limitsFacade.standing(subject, resource)).isEmpty();
    }

    @Test
    void shouldReportStoredUsedAndPeriodStartOnLiveWindow() {
        String resource = newResource();
        String subject = newSubject();
        Instant periodStart = Instant.now().minus(Duration.ofHours(1));
        seedConfig(resource, null, "FLOW", 5, "DAY");
        seedUsage(resource, subject, 3, periodStart);

        LimitStanding standing = limitsFacade.standing(subject, resource).orElseThrow();

        assertThat(standing.used()).isEqualTo(3);
        assertThat(standing.periodStart()).isCloseTo(periodStart, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void shouldReportZeroAndNoPeriodStartOnLapsedFlowWindow() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, "DAY");
        seedUsage(resource, subject, 4, Instant.now().minus(Duration.ofDays(2)));

        LimitStanding standing = limitsFacade.standing(subject, resource).orElseThrow();

        assertThat(standing.used()).isZero();
        assertThat(standing.periodStart()).isNull();
        assertThat(standing.resetsInSeconds()).isNull();
    }

    @Test
    void shouldLeaveStoredRowUntouchedWhenReportingLapsedWindowAsZero() {
        String resource = newResource();
        String subject = newSubject();
        Instant oldStart = Instant.now().minus(Duration.ofDays(2));
        seedConfig(resource, null, "FLOW", 5, "DAY");
        seedUsage(resource, subject, 4, oldStart);

        assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isZero();

        // Drop the period so nothing can lapse any more: the row the read reported as zero is still
        // the seeded one, which a standing that wrote its virtual reset back would have flattened.
        clearPeriod(resource, null);

        LimitStanding stored = limitsFacade.standing(subject, resource).orElseThrow();
        assertThat(stored.used()).isEqualTo(4);
        assertThat(stored.periodStart()).isCloseTo(oldStart, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void shouldCountDownToNextStartOnLiveFlowWindowWithPeriod() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, "DAY");
        seedUsage(resource, subject, 1, Instant.now().minus(Duration.ofHours(1)));

        Long resetsInSeconds = limitsFacade.standing(subject, resource).orElseThrow().resetsInSeconds();

        // The window started an hour ago and restarts a day after that, so 23 hours are left.
        assertThat(resetsInSeconds).isBetween(Duration.ofHours(23).minusMinutes(1).getSeconds(),
                Duration.ofHours(23).getSeconds());
    }

    @Test
    void shouldReportNoCountdownForStockAndForFlowWithoutPeriod() {
        String stockResource = newResource();
        String flowResource = newResource();
        String subject = newSubject();
        seedConfig(stockResource, null, "STOCK", 5, null);
        seedConfig(flowResource, null, "FLOW", 5, null);
        seedUsage(stockResource, subject, 2, Instant.now().minus(Duration.ofDays(365)));
        seedUsage(flowResource, subject, 2, Instant.now().minus(Duration.ofDays(365)));

        LimitStanding stock = limitsFacade.standing(subject, stockResource).orElseThrow();
        LimitStanding flow = limitsFacade.standing(subject, flowResource).orElseThrow();

        assertThat(stock.used()).isEqualTo(2);
        assertThat(stock.resetsInSeconds()).isNull();
        assertThat(flow.used()).isEqualTo(2);
        assertThat(flow.resetsInSeconds()).isNull();
    }

    @Test
    void shouldReportStandingWhenSubjectHasUsageRowButNoConfigurationAtAll() {
        String resource = newResource();
        String subject = newSubject();
        Instant periodStart = Instant.now().minus(Duration.ofDays(365));
        seedUsage(resource, subject, 7, periodStart);

        LimitStanding standing = limitsFacade.standing(subject, resource).orElseThrow();

        assertThat(standing.used()).isEqualTo(7);
        assertThat(standing.periodStart()).isCloseTo(periodStart, within(1, ChronoUnit.MILLIS));
        assertThat(standing.resetsInSeconds()).isNull();
    }

    @Test
    void shouldReturnOneCapPerConfiguredResourceWithOverrideBeatingDefault() {
        String overriddenResource = newResource();
        String defaultedResource = newResource();
        String subject = newSubject();
        seedConfig(overriddenResource, null, "STOCK", 5, null);
        seedConfig(overriddenResource, subject, "FLOW", 9, null);
        seedConfig(defaultedResource, null, "STOCK", 2, null);

        List<LimitCap> caps = limitsFacade.caps(subject);

        assertThat(caps).filteredOn(cap -> cap.resource().equals(overriddenResource))
                .containsExactly(new LimitCap(overriddenResource, LimitKind.FLOW, 9));
        assertThat(caps).filteredOn(cap -> cap.resource().equals(defaultedResource))
                .containsExactly(new LimitCap(defaultedResource, LimitKind.STOCK, 2));
    }

    @Test
    void shouldNotReturnAnotherSubjectsOverrideAmongCaps() {
        String resource = newResource();
        String subject = newSubject();
        String otherSubject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedConfig(resource, otherSubject, "STOCK", 99, null);

        assertThat(limitsFacade.caps(subject)).filteredOn(cap -> cap.resource().equals(resource))
                .containsExactly(new LimitCap(resource, LimitKind.STOCK, 5));
    }

    @Test
    void shouldResolveSingleCapWithOverrideBeatingDefault() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 5, null);
        seedConfig(resource, subject, "STOCK", 9, null);

        assertThat(limitsFacade.cap(subject, resource))
                .contains(new LimitCap(resource, LimitKind.STOCK, 9));
    }

    @Test
    void shouldReturnEmptyCapForUnconfiguredResource() {
        assertThat(limitsFacade.cap(newSubject(), newResource())).isEmpty();
    }

    @Nested
    @TestPropertySource(properties = "recipai.limits.enabled=false")
    class Disabled {

        @Test
        void shouldReturnNoCapsWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            seedConfig(resource, null, "STOCK", 5, null);

            assertThat(limitsFacade.caps(subject)).isEmpty();
        }

        @Test
        void shouldReturnEmptyCapWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            seedConfig(resource, null, "STOCK", 5, null);

            assertThat(limitsFacade.cap(subject, resource)).isEmpty();
        }

        @Test
        void shouldStillReportStandingWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            seedConfig(resource, null, "STOCK", 5, null);
            seedUsage(resource, subject, 3, Instant.now());

            assertThat(limitsFacade.standing(subject, resource).orElseThrow().used()).isEqualTo(3);
        }
    }

    private static String newResource() {
        return "TEST_LIMIT_" + UUID.randomUUID();
    }

    private static String newSubject() {
        return "subject-" + UUID.randomUUID();
    }

    private void seedConfig(String resource, String subject, String kind, int maxValue, String period) {
        jdbcClient.sql("""
                        INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                        VALUES (:id, :resource, :subject, :kind, :maxValue, :period)
                        """)
                .param("id", UUID.randomUUID())
                .param("resource", resource)
                .param("subject", subject)
                .param("kind", kind)
                .param("maxValue", maxValue)
                .param("period", period)
                .update();
    }

    private void seedUsage(String resource, String subject, int used, Instant periodStart) {
        jdbcClient.sql("""
                        INSERT INTO recipai.limit_usage (resource, subject, used, period_start)
                        VALUES (:resource, :subject, :used, :periodStart)
                        """)
                .param("resource", resource)
                .param("subject", subject)
                .param("used", used)
                // The column is TIMESTAMP without a zone and Hibernate reads it back as UTC, so the
                // seed has to write UTC wall-clock time — Timestamp.from would write the JVM's.
                .param("periodStart", LocalDateTime.ofInstant(periodStart, ZoneOffset.UTC))
                .update();
    }

    private void clearPeriod(String resource, String subject) {
        jdbcClient.sql("""
                        UPDATE recipai.limit_config
                           SET period = NULL
                         WHERE resource = :resource AND subject IS NOT DISTINCT FROM :subject
                        """)
                .param("resource", resource)
                .param("subject", subject)
                .update();
    }

    private void updateMaxValue(String resource, String subject, int maxValue) {
        jdbcClient.sql("""
                        UPDATE recipai.limit_config
                           SET max_value = :maxValue
                         WHERE resource = :resource AND subject IS NOT DISTINCT FROM :subject
                        """)
                .param("maxValue", maxValue)
                .param("resource", resource)
                .param("subject", subject)
                .update();
    }
}

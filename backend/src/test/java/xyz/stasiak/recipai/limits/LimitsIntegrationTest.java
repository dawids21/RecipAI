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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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
        setLimitQuota(resource, null, "FLOW", 5, null);

        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void shouldIncrementUsedOnEachSubsequentGrantWhileUnderLimit() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 5, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldRefuseOnceUsedEqualsMaxAndNotAdvancePastIt() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldPreferSubjectOverrideOverDefaultWhenLower() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 5, null);
        setLimitQuota(resource, subject, "FLOW", 1, null);

        limitsFacade.reserve(subject, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldPreferSubjectOverrideOverDefaultWhenHigher() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 1, null);
        setLimitQuota(resource, subject, "FLOW", 3, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldFallBackToResourceDefaultWhenSubjectHasNoOverride() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldAdmitNextReserveWithNoRestartAfterRaisingQuota() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);

        setLimitQuota(resource, null, "FLOW", 5, null);

        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldNeverRestartStockConfigurationEvenWithOldPeriodStart() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 3, null);
        setLimitUsage(resource, subject, 3, Instant.now().minus(Duration.ofDays(365)));

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldNeverRestartFlowWithNoPeriod() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, null);
        setLimitUsage(resource, subject, 2, Instant.now().minus(Duration.ofDays(365)));

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
    }

    @Test
    void shouldRestartFlowDayLazilyWhenPeriodStartTwoDaysOld() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, "DAY");
        Instant oldStart = Instant.now().minus(Duration.ofDays(2));
        setLimitUsage(resource, subject, 2, oldStart);

        limitsFacade.reserve(subject, resource);

        LimitBalance usage = limitsFacade.getBalance(subject, resource).orElseThrow();
        assertThat(usage.used()).isEqualTo(1);
        assertThat(usage.periodStart()).isAfter(oldStart.plus(Duration.ofDays(1)));
    }

    @Test
    void shouldNotRestartFlowDayWhenPeriodStartInsideWindow() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, "DAY");
        Instant recentStart = Instant.now().minus(Duration.ofHours(1));
        setLimitUsage(resource, subject, 2, recentStart);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldCarryResourceKindLimitAndUsedOnRefusal() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 1, null);

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
        setLimitQuota(resourceFlowDay, null, "FLOW", 1, "DAY");
        limitsFacade.reserve(subject1, resourceFlowDay);
        LimitExceededException flowEx = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject1, resourceFlowDay));
        assertThat(flowEx.retryAfterSeconds()).isPositive();

        String resourceStock = newResource();
        String subject2 = newSubject();
        setLimitQuota(resourceStock, null, "STOCK", 1, null);
        limitsFacade.reserve(subject2, resourceStock);
        LimitExceededException stockEx = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject2, resourceStock));
        assertThat(stockEx.retryAfterSeconds()).isNull();

        String resourceFlowNoPeriod = newResource();
        String subject3 = newSubject();
        setLimitQuota(resourceFlowNoPeriod, null, "FLOW", 1, null);
        limitsFacade.reserve(subject3, resourceFlowNoPeriod);
        LimitExceededException noPeriodEx = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject3, resourceFlowNoPeriod));
        assertThat(noPeriodEx.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldRefuseFirstReserveWhenMaxIsZero() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 0, null);

        LimitExceededException ex = catchThrowableOfType(LimitExceededException.class, 
                () -> limitsFacade.reserve(subject, resource));

        assertThat(ex).isNotNull();
        assertThat(ex.limit()).isZero();
        assertThat(ex.used()).isZero();
        assertThat(ex.retryAfterSeconds()).isNull();
        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldRefuseWhenMaxIsLoweredToZeroBelowExistingUsage() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 2, "DAY");

        limitsFacade.reserve(subject, resource);
        setLimitQuota(resource, null, "FLOW", 0, "DAY");

        LimitExceededException ex = catchThrowableOfType(LimitExceededException.class,
                () -> limitsFacade.reserve(subject, resource));

        assertThat(ex).isNotNull();
        assertThat(ex.limit()).isZero();
        assertThat(ex.used()).isEqualTo(1);
        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void shouldNotRestartElapsedPeriodWhenMaxIsZero() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 0, "DAY");
        Instant oldStart = Instant.now().minus(Duration.ofDays(2));
        setLimitUsage(resource, subject, 2, oldStart);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isZero();
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
        setLimitQuota(resource, null, "FLOW", 1, null);

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
        setLimitQuota(resourceA, null, "FLOW", 1, null);
        setLimitQuota(resourceB, null, "FLOW", 1, null);

        limitsFacade.reserve(subject, resourceA);
        limitsFacade.reserve(subject, resourceB);

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resourceA))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> limitsFacade.reserve(subject, resourceB))
                .isInstanceOf(LimitExceededException.class);
    }


    @Test
    void shouldDecrementUsedByOneForStockConfiguredSubject() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);
        setLimitUsage(resource, subject, 3, Instant.now());

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldLeaveUsedUnchangedForFlowConfiguredSubject() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 5, null);
        setLimitUsage(resource, subject, 3, Instant.now());

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldLeaveUsedUnchangedWhenSubjectFlowOverrideShadowsStockDefault() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);
        setLimitQuota(resource, subject, "FLOW", 5, null);
        setLimitUsage(resource, subject, 3, Instant.now());

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldFloorAtZeroWhenReleasingTwiceFromUsedOne() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);
        setLimitUsage(resource, subject, 1, Instant.now());

        limitsFacade.release(subject, resource);
        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(0);
    }

    @Test
    void shouldCreateNoRowWhenReleasingWithNoUsageRowPresent() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldReturnSilentlyWhenNoConfigurationResolvesAtAllOnRelease() {
        String resource = newResource();
        String subject = newSubject();

        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldAdmitReserveRefusedAtQuotaAfterOneRelease() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 1, null);

        limitsFacade.reserve(subject, resource);
        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);

        limitsFacade.release(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(1);
    }


    @Test
    void shouldResolveConfigurationFromConfigSubjectAndCountAgainstUsageSubject() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubject = newSubject();
        setLimitQuota(resource, null, "FLOW", 1, null);
        setLimitQuota(resource, configSubject, "FLOW", 3, null);

        limitsFacade.reserve(configSubject, usageSubject, resource);
        limitsFacade.reserve(configSubject, usageSubject, resource);
        limitsFacade.reserve(configSubject, usageSubject, resource);

        assertThat(limitsFacade.getBalance(usageSubject, resource).orElseThrow().used()).isEqualTo(3);
        assertThat(limitsFacade.getBalance(configSubject, resource)).isEmpty();
    }

    @Test
    void shouldCountTwoUsageSubjectsIndependentlyUnderOneConfigSubject() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubjectA = newSubject();
        String usageSubjectB = newSubject();
        setLimitQuota(resource, configSubject, "FLOW", 1, null);

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
        setLimitQuota(resource, configSubject, "FLOW", 1, null);

        limitsFacade.reserve(configSubject, usageSubjectA, resource);
        limitsFacade.reserve(configSubject, usageSubjectB, resource);
        assertThatThrownBy(() -> limitsFacade.reserve(configSubject, usageSubjectA, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> limitsFacade.reserve(configSubject, usageSubjectB, resource))
                .isInstanceOf(LimitExceededException.class);

        setLimitQuota(resource, configSubject, "FLOW", 2, null);

        limitsFacade.reserve(configSubject, usageSubjectA, resource);
        limitsFacade.reserve(configSubject, usageSubjectB, resource);

        assertThat(limitsFacade.getBalance(usageSubjectA, resource).orElseThrow().used()).isEqualTo(2);
        assertThat(limitsFacade.getBalance(usageSubjectB, resource).orElseThrow().used()).isEqualTo(2);
    }

    @Test
    void shouldRefuseWithConfigSubjectLimitAndUsageSubjectUsed() {
        String resource = newResource();
        String configSubject = newSubject();
        String usageSubject = newSubject();
        setLimitQuota(resource, configSubject, "FLOW", 1, null);

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
        setLimitQuota(resource, configSubject, "FLOW", 5, null);
        setLimitUsage(resource, usageSubject, 3, Instant.now());

        limitsFacade.release(configSubject, usageSubject, resource);
        assertThat(limitsFacade.getBalance(usageSubject, resource).orElseThrow().used()).isEqualTo(3);

        setLimitQuota(resource, configSubject, "STOCK", 5, null);

        limitsFacade.release(configSubject, usageSubject, resource);
        assertThat(limitsFacade.getBalance(usageSubject, resource).orElseThrow().used()).isEqualTo(2);
        assertThat(limitsFacade.getBalance(configSubject, resource)).isEmpty();
    }

    @Test
    void shouldBehaveIdenticallyForTwoArgumentAndThreeArgumentFormsWithEqualSubjects() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);

        limitsFacade.reserve(subject, subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(2);

        limitsFacade.release(subject, subject, resource);
        limitsFacade.release(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(0);
    }

    @Test
    void shouldDeleteUsageRowOnClear() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);
        setLimitUsage(resource, subject, 3, Instant.now());

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldDoNothingWhenClearingAbsentSubject() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldClearFlowConfiguredSubjectToo() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 5, null);
        setLimitUsage(resource, subject, 3, Instant.now());

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldClearWithNoConfigurationAtAll() {
        String resource = newResource();
        String subject = newSubject();
        setLimitUsage(resource, subject, 3, Instant.now());

        limitsFacade.clear(subject, resource);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldReturnEmptyBalanceWhenNoUsageRowExists() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);

        assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
    }

    @Test
    void shouldReportStoredUsedAndPeriodStartOnLiveWindow() {
        String resource = newResource();
        String subject = newSubject();
        Instant periodStart = Instant.now().minus(Duration.ofHours(1));
        setLimitQuota(resource, null, "FLOW", 5, "DAY");
        setLimitUsage(resource, subject, 3, periodStart);

        LimitBalance balance = limitsFacade.getBalance(subject, resource).orElseThrow();

        assertThat(balance.used()).isEqualTo(3);
        assertThat(balance.periodStart()).isCloseTo(periodStart, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void shouldReportZeroAndNoPeriodStartOnPassedFlowWindow() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 5, "DAY");
        setLimitUsage(resource, subject, 4, Instant.now().minus(Duration.ofDays(2)));

        LimitBalance balance = limitsFacade.getBalance(subject, resource).orElseThrow();

        assertThat(balance.used()).isZero();
        assertThat(balance.periodStart()).isNull();
        assertThat(balance.resetsInSeconds()).isNull();
    }

    @Test
    void shouldLeaveStoredRowUntouchedWhenReportingPassedWindowAsZero() {
        String resource = newResource();
        String subject = newSubject();
        Instant oldStart = Instant.now().minus(Duration.ofDays(2));
        setLimitQuota(resource, null, "FLOW", 5, "DAY");
        setLimitUsage(resource, subject, 4, oldStart);

        assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isZero();

        // Drop the period so nothing can lapse any more: the row the read reported as zero is still
        // the seeded one, which a balance that wrote its virtual reset back would have flattened.
        setLimitQuota(resource, null, "FLOW", 5, null);

        LimitBalance stored = limitsFacade.getBalance(subject, resource).orElseThrow();
        assertThat(stored.used()).isEqualTo(4);
        assertThat(stored.periodStart()).isCloseTo(oldStart, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void shouldCountDownToNextStartOnLiveFlowWindowWithPeriod() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "FLOW", 5, "DAY");
        setLimitUsage(resource, subject, 1, Instant.now().minus(Duration.ofHours(1)));

        Long resetsInSeconds = limitsFacade.getBalance(subject, resource).orElseThrow().resetsInSeconds();

        // The window started an hour ago and restarts a day after that, so 23 hours are left.
        assertThat(resetsInSeconds).isBetween(Duration.ofHours(23).minusMinutes(1).getSeconds(),
                Duration.ofHours(23).getSeconds());
    }

    @Test
    void shouldReportNoCountdownForStockAndForFlowWithoutPeriod() {
        String stockResource = newResource();
        String flowResource = newResource();
        String subject = newSubject();
        setLimitQuota(stockResource, null, "STOCK", 5, null);
        setLimitQuota(flowResource, null, "FLOW", 5, null);
        setLimitUsage(stockResource, subject, 2, Instant.now().minus(Duration.ofDays(365)));
        setLimitUsage(flowResource, subject, 2, Instant.now().minus(Duration.ofDays(365)));

        LimitBalance stock = limitsFacade.getBalance(subject, stockResource).orElseThrow();
        LimitBalance flow = limitsFacade.getBalance(subject, flowResource).orElseThrow();

        assertThat(stock.used()).isEqualTo(2);
        assertThat(stock.resetsInSeconds()).isNull();
        assertThat(flow.used()).isEqualTo(2);
        assertThat(flow.resetsInSeconds()).isNull();
    }

    @Test
    void shouldReportBalanceWhenSubjectHasUsageRowButNoConfigurationAtAll() {
        String resource = newResource();
        String subject = newSubject();
        Instant periodStart = Instant.now().minus(Duration.ofDays(365));
        setLimitUsage(resource, subject, 7, periodStart);

        LimitBalance balance = limitsFacade.getBalance(subject, resource).orElseThrow();

        assertThat(balance.used()).isEqualTo(7);
        assertThat(balance.periodStart()).isCloseTo(periodStart, within(1, ChronoUnit.MILLIS));
        assertThat(balance.resetsInSeconds()).isNull();
    }

    @Test
    void shouldReturnOneQuotaPerConfiguredResourceWithOverrideBeatingDefault() {
        String overriddenResource = newResource();
        String defaultedResource = newResource();
        String subject = newSubject();
        setLimitQuota(overriddenResource, null, "STOCK", 5, null);
        setLimitQuota(overriddenResource, subject, "FLOW", 9, null);
        setLimitQuota(defaultedResource, null, "STOCK", 2, null);

        List<LimitQuota> quotas = limitsFacade.getQuotas(subject);

        assertThat(quotas).filteredOn(quota -> quota.resource().equals(overriddenResource))
                .containsExactly(new LimitQuota(overriddenResource, LimitKind.FLOW, 9));
        assertThat(quotas).filteredOn(quota -> quota.resource().equals(defaultedResource))
                .containsExactly(new LimitQuota(defaultedResource, LimitKind.STOCK, 2));
    }

    @Test
    void shouldNotReturnAnotherSubjectsOverrideAmongQuotas() {
        String resource = newResource();
        String subject = newSubject();
        String otherSubject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);
        setLimitQuota(resource, otherSubject, "STOCK", 99, null);

        assertThat(limitsFacade.getQuotas(subject)).filteredOn(quota -> quota.resource().equals(resource))
                .containsExactly(new LimitQuota(resource, LimitKind.STOCK, 5));
    }

    @Test
    void shouldResolveSingleQuotaWithOverrideBeatingDefault() {
        String resource = newResource();
        String subject = newSubject();
        setLimitQuota(resource, null, "STOCK", 5, null);
        setLimitQuota(resource, subject, "STOCK", 9, null);

        assertThat(limitsFacade.getQuota(subject, resource))
                .contains(new LimitQuota(resource, LimitKind.STOCK, 9));
    }

    @Test
    void shouldReturnEmptyQuotaForUnconfiguredResource() {
        assertThat(limitsFacade.getQuota(newSubject(), newResource())).isEmpty();
    }

    @Nested
    @TestPropertySource(properties = "recipai.limits.enabled=false")
    class Disabled {

        @Test
        void shouldReturnNoQuotasWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 5, null);

            assertThat(limitsFacade.getQuotas(subject)).isEmpty();
        }

        @Test
        void shouldReturnEmptyQuotaWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 5, null);

            assertThat(limitsFacade.getQuota(subject, resource)).isEmpty();
        }

        @Test
        void shouldStillReportBalanceWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 5, null);
            setLimitUsage(resource, subject, 3, Instant.now());

            assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
        }

        @Test
        void shouldKeepCountingPastTheConfiguredMaximumWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 2, null);

            limitsFacade.reserve(subject, resource);
            limitsFacade.reserve(subject, resource);
            limitsFacade.reserve(subject, resource);
            limitsFacade.reserve(subject, resource);

            assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(4);
        }

        @Test
        void shouldGrantReserveAtAndOverTheMaximumWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 1, null);

            limitsFacade.reserve(subject, resource);

            assertThatNoException().isThrownBy(() -> limitsFacade.reserve(subject, resource));
            assertThatNoException().isThrownBy(() -> limitsFacade.reserve(subject, resource));
            assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(3);
        }

        @Test
        void shouldStillDecrementOnReleaseWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 5, null);

            limitsFacade.reserve(subject, resource);
            limitsFacade.reserve(subject, resource);
            limitsFacade.release(subject, resource);

            assertThat(limitsFacade.getBalance(subject, resource).orElseThrow().used()).isEqualTo(1);
        }

        @Test
        void shouldStillThrowConfigurationMissingWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();

            assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                    .isInstanceOf(LimitConfigurationMissingException.class);
        }

        @Test
        void shouldStillDeleteTheUsageRowOnClearWhenLimitsAreDisabled() {
            String resource = newResource();
            String subject = newSubject();
            setLimitQuota(resource, null, "STOCK", 5, null);

            limitsFacade.reserve(subject, resource);
            limitsFacade.clear(subject, resource);

            assertThat(limitsFacade.getBalance(subject, resource)).isEmpty();
        }
    }

    private static String newResource() {
        return "TEST_LIMIT_" + UUID.randomUUID();
    }

    private static String newSubject() {
        return "subject-" + UUID.randomUUID();
    }

    /**
     * Upserts the quota: {@code limit_config} has no write API, so there is no business path to it.
     */
    private void setLimitQuota(String resource, String subject, String kind, int maxValue, String period) {
        jdbcClient.sql("""
                        INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                        VALUES (:id, :resource, :subject, :kind, :maxValue, :period)
                        ON CONFLICT (resource, subject) DO UPDATE SET
                            kind      = EXCLUDED.kind,
                            max_value = EXCLUDED.max_value,
                            period    = EXCLUDED.period
                        """)
                .param("id", UUID.randomUUID())
                .param("resource", resource)
                .param("subject", subject)
                .param("kind", kind)
                .param("maxValue", maxValue)
                .param("period", period)
                .update();
    }

    /**
     * Fabricates a usage row directly: the seeded {@code used} and {@code period_start} pairs here are
     * states no reserve could have produced at this instant.
     */
    private void setLimitUsage(String resource, String subject, int used, Instant periodStart) {
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


}

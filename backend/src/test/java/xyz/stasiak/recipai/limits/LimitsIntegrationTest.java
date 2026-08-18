package xyz.stasiak.recipai.limits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void shouldIncrementUsedOnEachSubsequentGrantWhileUnderLimit() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "FLOW", 5, null);

        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);
        limitsFacade.reserve(subject, resource);

        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(3);
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
        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(2);
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

        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(3);
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

        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(3);
    }

    @Test
    void shouldNeverRestartStockConfigurationEvenWithOldPeriodStart() {
        String resource = newResource();
        String subject = newSubject();
        seedConfig(resource, null, "STOCK", 3, null);
        seedUsage(resource, subject, 3, Instant.now().minus(Duration.ofDays(365)));

        assertThatThrownBy(() -> limitsFacade.reserve(subject, resource))
                .isInstanceOf(LimitExceededException.class);
        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(3);
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

        LimitUsageDetails usage = limitsFacade.currentUsage(subject, resource).orElseThrow();
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
        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(2);
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
        assertThat(limitsFacade.currentUsage(subject, resource)).isEmpty();
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
        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(1);
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
        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(2);
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
        assertThat(limitsFacade.currentUsage(subject, resource).orElseThrow().used()).isEqualTo(5);
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
                .param("periodStart", Timestamp.from(periodStart))
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

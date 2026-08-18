package xyz.stasiak.recipai.limits;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class LimitPeriodTest {

    @Test
    void shouldSubtractExactly24HoursForDay() {
        Instant now = ZonedDateTime.of(2026, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Instant cutoff = LimitPeriod.DAY.cutoffFrom(now);

        assertThat(cutoff).isEqualTo(now.minusSeconds(24 * 3600));
    }

    @Test
    void shouldSubtractExactly7DaysForWeek() {
        Instant now = ZonedDateTime.of(2026, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Instant cutoff = LimitPeriod.WEEK.cutoffFrom(now);

        assertThat(cutoff).isEqualTo(now.minusSeconds(7 * 24 * 3600));
    }

    @Test
    void shouldSubtractCalendarMonthForMonthNotThirtyDays() {
        Instant now = ZonedDateTime.of(2026, 3, 31, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Instant cutoff = LimitPeriod.MONTH.cutoffFrom(now);

        Instant expected = ZonedDateTime.of(2026, 2, 28, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
        assertThat(cutoff).isEqualTo(expected);
    }

    @Test
    void shouldAddExactly24HoursForDayAnd7DaysForWeek() {
        Instant start = ZonedDateTime.of(2026, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();

        assertThat(LimitPeriod.DAY.nextStart(start)).isEqualTo(start.plusSeconds(24 * 3600));
        assertThat(LimitPeriod.WEEK.nextStart(start)).isEqualTo(start.plusSeconds(7 * 24 * 3600));
    }

    @Test
    void shouldAddCalendarMonthForMonthClampingTheDay() {
        Instant start = ZonedDateTime.of(2026, 1, 31, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Instant next = LimitPeriod.MONTH.nextStart(start);

        Instant expected = ZonedDateTime.of(2026, 2, 28, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
        assertThat(next).isEqualTo(expected);
    }

    /**
     * Sets the JVM default zone, which is global state; it is restored in the finally block and the
     * suite runs sequentially. Controlling the default is unavoidable here — the whole point is that
     * {@link LimitPeriod} ignores it, which cannot be observed while it equals UTC.
     */
    @Test
    void shouldPerformMonthArithmeticAtUtcRegardlessOfJvmDefaultZone() {
        TimeZone originalDefault = TimeZone.getDefault();
        try {
            // Both instants fall on a different calendar day in Pacific/Niue (UTC-11) than in UTC,
            // so a system-zone implementation clamps a different month end and lands a day later.
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Niue"));

            Instant now = ZonedDateTime.of(2026, 3, 31, 2, 0, 0, 0, ZoneOffset.UTC).toInstant();
            assertThat(LimitPeriod.MONTH.cutoffFrom(now))
                    .isEqualTo(ZonedDateTime.of(2026, 2, 28, 2, 0, 0, 0, ZoneOffset.UTC).toInstant());

            Instant periodStart = ZonedDateTime.of(2026, 1, 31, 2, 0, 0, 0, ZoneOffset.UTC).toInstant();
            assertThat(LimitPeriod.MONTH.nextStart(periodStart))
                    .isEqualTo(ZonedDateTime.of(2026, 2, 28, 2, 0, 0, 0, ZoneOffset.UTC).toInstant());
        } finally {
            TimeZone.setDefault(originalDefault);
        }
    }

    @Test
    void cutoffFromIsAlwaysStrictlyBeforeNowAndNextStartIsAlwaysStrictlyAfter() {
        Instant now = ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

        for (LimitPeriod period : LimitPeriod.values()) {
            assertThat(period.cutoffFrom(now)).isBefore(now);
            assertThat(period.nextStart(now)).isAfter(now);
        }
    }
}

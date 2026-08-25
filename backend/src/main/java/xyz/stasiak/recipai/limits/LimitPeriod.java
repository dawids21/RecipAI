package xyz.stasiak.recipai.limits;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

enum LimitPeriod {
    DAY, WEEK, MONTH;

    Instant cutoffFrom(Instant now) {
        return switch (this) {
            case DAY -> now.minus(Duration.ofDays(1));
            case WEEK -> now.minus(Duration.ofDays(7));
            case MONTH -> now.atZone(ZoneOffset.UTC).minus(Period.ofMonths(1)).toInstant();
        };
    }

    long secondsUntilNextStart(Instant periodStart, Instant now) {
        return Math.max(1, Duration.between(now, nextStart(periodStart)).getSeconds());
    }

    Instant nextStart(Instant periodStart) {
        return switch (this) {
            case DAY -> periodStart.plus(Duration.ofDays(1));
            case WEEK -> periodStart.plus(Duration.ofDays(7));
            case MONTH -> periodStart.atZone(ZoneOffset.UTC).plus(Period.ofMonths(1)).toInstant();
        };
    }
}

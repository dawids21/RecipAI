package xyz.stasiak.recipai.limits;

import java.time.Instant;

public record LimitStanding(int used, Instant periodStart, Long resetsInSeconds) {
}

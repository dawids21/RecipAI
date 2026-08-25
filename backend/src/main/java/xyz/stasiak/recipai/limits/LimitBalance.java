package xyz.stasiak.recipai.limits;

import java.time.Instant;

public record LimitBalance(int used, Instant periodStart, Long resetsInSeconds) {

    public static LimitBalance zero() {
        return new LimitBalance(0, null, null);
    }
}

package xyz.stasiak.recipai.limits;

import java.time.Instant;

public record LimitUsageDetails(int used, Instant periodStart) {
}

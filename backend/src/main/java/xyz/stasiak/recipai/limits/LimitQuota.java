package xyz.stasiak.recipai.limits;

public record LimitQuota(String resource, LimitKind kind, int limit) {
}

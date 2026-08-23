package xyz.stasiak.recipai.limits;

public record LimitCap(String resource, LimitKind kind, int limit) {
}

package xyz.stasiak.recipai.limits;

public class LimitExceededException extends RuntimeException {

    private final String resource;
    private final LimitKind kind;
    private final int limit;
    private final int used;
    private final Long retryAfterSeconds;

    LimitExceededException(String resource, LimitKind kind, int limit, int used, Long retryAfterSeconds) {
        super("Limit for " + resource + " reached (" + used + " of " + limit + " used)");
        this.resource = resource;
        this.kind = kind;
        this.limit = limit;
        this.used = used;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String resource() {
        return resource;
    }

    public LimitKind kind() {
        return kind;
    }

    public int limit() {
        return limit;
    }

    public int used() {
        return used;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

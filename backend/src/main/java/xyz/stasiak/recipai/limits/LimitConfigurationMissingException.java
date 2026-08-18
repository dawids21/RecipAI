package xyz.stasiak.recipai.limits;

public class LimitConfigurationMissingException extends RuntimeException {

    private final String resource;

    LimitConfigurationMissingException(String resource) {
        super("No limit configuration found for resource: " + resource);
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}

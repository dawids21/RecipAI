package xyz.stasiak.recipai.permissions.exception;

import java.util.UUID;

public class ResourceAccessDeniedException extends RuntimeException {

    public ResourceAccessDeniedException(String resourceType, UUID resourceId) {
        super("Access denied to " + resourceType + " with id: " + resourceId);
    }
}

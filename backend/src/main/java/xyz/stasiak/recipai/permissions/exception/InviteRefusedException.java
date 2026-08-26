package xyz.stasiak.recipai.permissions.exception;

public class InviteRefusedException extends RuntimeException {

    private final Reason reason;

    public InviteRefusedException(String resourceType, String targetEmail, Reason reason) {
        super("Invite for " + resourceType + " to " + targetEmail + " refused: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        ALREADY_INVITED,
        ALREADY_HAS_ACCESS
    }
}

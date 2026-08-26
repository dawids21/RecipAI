package xyz.stasiak.recipai.permissions.exception;

import java.util.UUID;

public class InviteNotFoundException extends RuntimeException {

    public InviteNotFoundException(UUID inviteId) {
        super("Invite not found with id: " + inviteId);
    }
}

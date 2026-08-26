package xyz.stasiak.recipai.permissions.exception;

import xyz.stasiak.recipai.permissions.dto.ResourceRole;

public class InvalidInviteRoleException extends RuntimeException {

    public InvalidInviteRoleException(ResourceRole role) {
        super("Cannot invite at role: " + role);
    }
}

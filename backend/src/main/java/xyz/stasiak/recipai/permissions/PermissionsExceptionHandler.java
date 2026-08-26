package xyz.stasiak.recipai.permissions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.stasiak.recipai.permissions.exception.InvalidInviteRoleException;
import xyz.stasiak.recipai.permissions.exception.InviteNotFoundException;
import xyz.stasiak.recipai.permissions.exception.InviteRefusedException;
import xyz.stasiak.recipai.permissions.exception.ResourceAccessDeniedException;

@RestControllerAdvice
class PermissionsExceptionHandler {

    @ExceptionHandler(ResourceAccessDeniedException.class)
    ProblemDetail handleResourceAccessDenied(ResourceAccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problemDetail.setTitle("Resource Access Denied");
        return problemDetail;
    }

    @ExceptionHandler(InviteNotFoundException.class)
    ProblemDetail handleInviteNotFound(InviteNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Invite Not Found");
        return problemDetail;
    }

    @ExceptionHandler(InviteRefusedException.class)
    ProblemDetail handleInviteRefused(InviteRefusedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Invite Refused");
        problemDetail.setProperty("reason", ex.reason());
        return problemDetail;
    }

    @ExceptionHandler(InvalidInviteRoleException.class)
    ProblemDetail handleInvalidInviteRole(InvalidInviteRoleException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid Invite Role");
        return problemDetail;
    }
}

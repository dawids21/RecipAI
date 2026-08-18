package xyz.stasiak.recipai.limits;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class LimitsExceptionHandler {

    @ExceptionHandler(LimitExceededException.class)
    ResponseEntity<ProblemDetail> handleLimitExceeded(LimitExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problemDetail.setTitle("Limit Exceeded");
        problemDetail.setProperty("resource", ex.resource());
        problemDetail.setProperty("kind", ex.kind());
        problemDetail.setProperty("limit", ex.limit());
        problemDetail.setProperty("used", ex.used());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (ex.retryAfterSeconds() != null) {
            problemDetail.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
            response.header("Retry-After", String.valueOf(ex.retryAfterSeconds()));
        }
        return response.body(problemDetail);
    }

    @ExceptionHandler(LimitConfigurationMissingException.class)
    ResponseEntity<ProblemDetail> handleLimitConfigurationMissing(LimitConfigurationMissingException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problemDetail.setTitle("Limit Configuration Missing");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}

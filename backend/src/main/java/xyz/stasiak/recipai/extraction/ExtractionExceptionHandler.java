package xyz.stasiak.recipai.extraction;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ExtractionExceptionHandler {

    @ExceptionHandler(UnsupportedImageTypeException.class)
    ProblemDetail handleUnsupportedImageType(UnsupportedImageTypeException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Unsupported Image Type");
        return problemDetail;
    }

    @ExceptionHandler(ExtractionFailedException.class)
    ProblemDetail handleExtractionFailed(ExtractionFailedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problemDetail.setTitle("Extraction Failed");
        return problemDetail;
    }
}

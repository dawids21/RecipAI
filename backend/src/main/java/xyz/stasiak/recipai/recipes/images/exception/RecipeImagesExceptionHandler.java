package xyz.stasiak.recipai.recipes.images.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class RecipeImagesExceptionHandler {

    @ExceptionHandler(InvalidImageException.class)
    public ProblemDetail handleInvalidImageException(InvalidImageException ex) {
        log.error("Invalid image error: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid Image");
        return problemDetail;
    }

    @ExceptionHandler(ImageLimitExceededException.class)
    public ProblemDetail handleImageLimitExceededException(ImageLimitExceededException ex) {
        log.error("Image limit exceeded: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Image Limit Exceeded");
        return problemDetail;
    }
}

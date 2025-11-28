package xyz.stasiak.recipai.recipes.collections;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionAccessDeniedException;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionNotFoundException;

@ControllerAdvice
class RecipesCollectionsExceptionHandler {

    @ExceptionHandler(RecipesCollectionNotFoundException.class)
    public ProblemDetail handleRecipesCollectionNotFound(RecipesCollectionNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Recipes Collection Not Found");
        return problemDetail;
    }

    @ExceptionHandler(RecipesCollectionAccessDeniedException.class)
    public ProblemDetail handleRecipesCollectionAccessDenied(RecipesCollectionAccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage()
        );
        problemDetail.setTitle("Recipes Collection Access Denied");
        return problemDetail;
    }
}
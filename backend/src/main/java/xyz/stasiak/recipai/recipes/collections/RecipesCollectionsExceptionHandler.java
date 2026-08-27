package xyz.stasiak.recipai.recipes.collections;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionNotFoundException;

@RestControllerAdvice
class RecipesCollectionsExceptionHandler {

    @ExceptionHandler(RecipesCollectionNotFoundException.class)
    ProblemDetail handleRecipesCollectionNotFound(RecipesCollectionNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Recipes Collection Not Found");
        return problemDetail;
    }
}

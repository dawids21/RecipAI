package xyz.stasiak.recipai.recipes;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RecipesExceptionHandler {

    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail handleRecipeNotFound(RecipeNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Recipe Not Found");
        return problemDetail;
    }
}

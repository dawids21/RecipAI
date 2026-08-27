package xyz.stasiak.recipai.planning;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.stasiak.recipai.planning.exception.*;

@RestControllerAdvice
class PlanningExceptionHandler {

    @ExceptionHandler(MealPlanNotFoundException.class)
    ProblemDetail handleMealPlanNotFound(MealPlanNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Meal Plan Not Found");
        return problemDetail;
    }

    @ExceptionHandler(MealPlanEntryNotFoundException.class)
    ProblemDetail handleMealPlanEntryNotFound(MealPlanEntryNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Meal Plan Entry Not Found");
        return problemDetail;
    }

    @ExceptionHandler(InvalidMealPlanEntryException.class)
    ProblemDetail handleInvalidMealPlanEntry(InvalidMealPlanEntryException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Invalid Meal Plan Entry");
        return problemDetail;
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    ProblemDetail handleInvalidDateRange(InvalidDateRangeException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Invalid Date Range");
        return problemDetail;
    }
}

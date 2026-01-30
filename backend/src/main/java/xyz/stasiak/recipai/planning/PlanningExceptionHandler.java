package xyz.stasiak.recipai.planning;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import xyz.stasiak.recipai.planning.exception.*;

@ControllerAdvice
class PlanningExceptionHandler {

    @ExceptionHandler(MealPlanNotFoundException.class)
    public ProblemDetail handleMealPlanNotFound(MealPlanNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Meal Plan Not Found");
        return problemDetail;
    }

    @ExceptionHandler(MealPlanAccessDeniedException.class)
    public ProblemDetail handleMealPlanAccessDenied(MealPlanAccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage()
        );
        problemDetail.setTitle("Meal Plan Access Denied");
        return problemDetail;
    }

    @ExceptionHandler(MealPlanEntryNotFoundException.class)
    public ProblemDetail handleMealPlanEntryNotFound(MealPlanEntryNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Meal Plan Entry Not Found");
        return problemDetail;
    }

    @ExceptionHandler(MealPlanLimitExceededException.class)
    public ProblemDetail handleMealPlanLimitExceeded(MealPlanLimitExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setTitle("Meal Plan Limit Exceeded");
        return problemDetail;
    }

    @ExceptionHandler(InvalidMealPlanEntryException.class)
    public ProblemDetail handleInvalidMealPlanEntry(InvalidMealPlanEntryException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setTitle("Invalid Meal Plan Entry");
        return problemDetail;
    }
}

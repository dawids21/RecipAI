package xyz.stasiak.recipai.shoppinglists;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
class ShoppingListsExceptionHandler {

    @ExceptionHandler(ShoppingListNotFoundException.class)
    public ProblemDetail handleShoppingListNotFound(ShoppingListNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Shopping List Not Found");
        return problemDetail;
    }

    @ExceptionHandler(ShoppingListAccessDeniedException.class)
    public ProblemDetail handleShoppingListAccessDenied(ShoppingListAccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage()
        );
        problemDetail.setTitle("Shopping List Access Denied");
        return problemDetail;
    }
}

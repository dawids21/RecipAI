package xyz.stasiak.recipai.shoppinglists;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListAccessDeniedException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListItemNotFoundException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListItemVersionMismatchException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListNotFoundException;

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

    @ExceptionHandler(ShoppingListItemNotFoundException.class)
    public ProblemDetail handleShoppingListItemNotFound(ShoppingListItemNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Shopping List Item Not Found");
        return problemDetail;
    }

    @ExceptionHandler(ShoppingListItemVersionMismatchException.class)
    public ProblemDetail handleShoppingListItemVersionMismatch(ShoppingListItemVersionMismatchException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PRECONDITION_FAILED,
                ex.getMessage()
        );
        problemDetail.setTitle("Shopping List Item Version Mismatch");
        return problemDetail;
    }
}

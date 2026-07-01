package xyz.stasiak.recipai.shoppinglists;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListItemDto;
import xyz.stasiak.recipai.shoppinglists.exception.ItemNotFoundException;
import xyz.stasiak.recipai.shoppinglists.exception.ItemVersionConflictException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListAccessDeniedException;
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

    @ExceptionHandler(ItemNotFoundException.class)
    public ProblemDetail handleItemNotFound(ItemNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setTitle("Shopping List Item Not Found");
        return problemDetail;
    }

    @ExceptionHandler(ItemVersionConflictException.class)
    public ResponseEntity<ShoppingListItemDto> handleItemVersionConflict(ItemVersionConflictException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(ex.winningItem());
    }

}

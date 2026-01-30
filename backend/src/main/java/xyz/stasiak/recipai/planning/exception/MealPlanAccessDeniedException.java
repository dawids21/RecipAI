package xyz.stasiak.recipai.planning.exception;

import java.util.UUID;

public class MealPlanAccessDeniedException extends RuntimeException {

    public MealPlanAccessDeniedException(UUID id) {
        super("Access denied to meal plan with id: " + id);
    }
}

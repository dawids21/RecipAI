package xyz.stasiak.recipai.planning.exception;

import java.util.UUID;

public class MealPlanNotFoundException extends RuntimeException {

    public MealPlanNotFoundException(UUID id) {
        super("Meal plan not found with id: " + id);
    }
}

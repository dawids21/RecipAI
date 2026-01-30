package xyz.stasiak.recipai.planning.exception;

public class MealPlanEntryNotFoundException extends RuntimeException {

    public MealPlanEntryNotFoundException(Long id) {
        super("Meal plan entry not found with id: " + id);
    }
}

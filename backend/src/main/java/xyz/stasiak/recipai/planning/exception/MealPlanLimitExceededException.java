package xyz.stasiak.recipai.planning.exception;

public class MealPlanLimitExceededException extends RuntimeException {

    public MealPlanLimitExceededException() {
        super("Maximum number of owned meal plans (10) has been reached");
    }
}

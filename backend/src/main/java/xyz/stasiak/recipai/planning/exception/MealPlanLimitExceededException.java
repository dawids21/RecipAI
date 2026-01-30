package xyz.stasiak.recipai.planning.exception;

public class MealPlanLimitExceededException extends RuntimeException {

    public MealPlanLimitExceededException(int maxOwnedPlans) {
        super("Maximum number of owned meal plans (" + maxOwnedPlans + ") has been reached");
    }
}

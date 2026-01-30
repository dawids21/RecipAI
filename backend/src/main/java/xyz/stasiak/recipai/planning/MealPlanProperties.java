package xyz.stasiak.recipai.planning;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recipai.meal-plan")
record MealPlanProperties(
        int maxOwnedPlans
) {
}
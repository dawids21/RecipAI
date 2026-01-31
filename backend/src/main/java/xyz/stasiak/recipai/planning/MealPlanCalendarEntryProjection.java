package xyz.stasiak.recipai.planning;

import java.time.LocalDate;
import java.util.UUID;

interface MealPlanCalendarEntryProjection {
    Long getId();

    UUID getPlanId();

    String getPlanColor();

    LocalDate getDate();

    UUID getRecipeId();

    String getRecipeName();

    String getPlaceholderText();

    Integer getServingSize();

    Boolean getHasRecipeAccess();
}

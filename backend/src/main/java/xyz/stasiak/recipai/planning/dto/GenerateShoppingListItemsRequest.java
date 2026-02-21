package xyz.stasiak.recipai.planning.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GenerateShoppingListItemsRequest(
        @NotNull @NotEmpty List<UUID> planIds,
        @NotNull @NotEmpty List<LocalDate> selectedDates
) {
}

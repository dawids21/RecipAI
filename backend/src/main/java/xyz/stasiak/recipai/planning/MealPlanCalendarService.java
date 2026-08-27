package xyz.stasiak.recipai.planning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.permissions.PermissionsFacade;
import xyz.stasiak.recipai.planning.dto.MealPlanCalendarViewDto;
import xyz.stasiak.recipai.planning.exception.InvalidDateRangeException;
import xyz.stasiak.recipai.recipes.RecipeFacade;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class MealPlanCalendarService {

    private final MealPlanEntryRepository entryRepository;
    private final PermissionsFacade permissionsFacade;
    private final RecipeFacade recipeFacade;

    Map<LocalDate, List<MealPlanCalendarViewDto>> getCalendarView(
            String userEmail,
            LocalDate startDate,
            LocalDate endDate,
            List<UUID> requestedPlanIds) {

        validateDateRange(startDate, endDate);

        Set<UUID> accessiblePlanIds = permissionsFacade.accessibleResources(MealPlanService.MEAL_PLAN_RESOURCE, userEmail).keySet();
        List<UUID> planIds = requestedPlanIds.stream().filter(accessiblePlanIds::contains).toList();
        if (planIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> recipeIds = recipeFacade.getAccessibleRecipeIds(userEmail);

        List<MealPlanCalendarEntryProjection> projections =
                entryRepository.findCalendarEntries(startDate, endDate, planIds, recipeIds);

        List<MealPlanCalendarViewDto> dtos = projections.stream()
                .map(this::toDto)
                .toList();

        return dtos.stream()
                .collect(Collectors.groupingBy(
                        MealPlanCalendarViewDto::date,
                        TreeMap::new,
                        Collectors.toList()
                ));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must be before or equal to endDate");
        }

        long monthsBetween = ChronoUnit.MONTHS.between(startDate, endDate);
        if (monthsBetween > 3) {
            throw new InvalidDateRangeException("Date range cannot exceed 3 months");
        }
    }

    private MealPlanCalendarViewDto toDto(MealPlanCalendarEntryProjection projection) {
        return new MealPlanCalendarViewDto(
                projection.getId(),
                projection.getPlanId(),
                projection.getPlanColor(),
                projection.getDate(),
                projection.getRecipeId(),
                projection.getRecipeName(),
                projection.getPlaceholderText(),
                projection.getServingSize(),
                projection.getHasRecipeAccess()
        );
    }
}

package xyz.stasiak.recipai.planning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.planning.dto.MealPlanCalendarViewDto;
import xyz.stasiak.recipai.planning.exception.InvalidDateRangeException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class MealPlanCalendarService {

    private final MealPlanEntryRepository entryRepository;

    Map<LocalDate, List<MealPlanCalendarViewDto>> getCalendarView(
            String userEmail,
            LocalDate startDate,
            LocalDate endDate,
            List<UUID> planIds) {

        validateDateRange(startDate, endDate);

        List<MealPlanCalendarEntryProjection> projections =
                entryRepository.findCalendarEntries(userEmail, startDate, endDate, planIds);

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

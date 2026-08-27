package xyz.stasiak.recipai.planning;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.permissions.dto.UnshareRequest;
import xyz.stasiak.recipai.planning.dto.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/meal-plans")
@RequiredArgsConstructor
@Slf4j
class MealPlanController {

    private final MealPlanService mealPlanService;
    private final MealPlanCalendarService calendarService;

    @GetMapping("/balance")
    LimitBalance getBalance(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting meal plan balance for user: {}", userEmail);
        return mealPlanService.balance(userEmail);
    }

    @GetMapping
    List<MealPlanDto> getAllMealPlans(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting meal plans for user: {}", userEmail);
        return mealPlanService.findAll(userEmail);
    }

    @PostMapping
    ResponseEntity<MealPlanDto> createMealPlan(@Valid @RequestBody CreateMealPlanRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating meal plan for user: {}", userEmail);
        MealPlanDto dto = mealPlanService.create(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    ResponseEntity<MealPlanDto> updateMealPlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMealPlanRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating meal plan with id: {} for user: {}", id, userEmail);
        MealPlanDto dto = mealPlanService.update(id, request, userEmail);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteMealPlan(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting meal plan with id: {} for user: {}", id, userEmail);
        mealPlanService.delete(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{planId}/entries")
    ResponseEntity<MealPlanEntryDto> createEntry(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateMealPlanEntryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating entry for meal plan {} by user {}", planId, userEmail);
        MealPlanEntryDto dto = mealPlanService.createEntry(planId, request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{planId}/entries/{entryId}")
    ResponseEntity<MealPlanEntryDto> updateEntry(
            @PathVariable UUID planId,
            @PathVariable Long entryId,
            @Valid @RequestBody UpdateMealPlanEntryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating entry {} for meal plan {} by user {}", entryId, planId, userEmail);
        MealPlanEntryDto dto = mealPlanService.updateEntry(planId, entryId, request, userEmail);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{planId}/entries/{entryId}")
    ResponseEntity<Void> deleteEntry(
            @PathVariable UUID planId,
            @PathVariable Long entryId,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting entry {} from meal plan {} by user {}", entryId, planId, userEmail);
        mealPlanService.deleteEntry(planId, entryId, userEmail);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    ResponseEntity<List<PermissionDto>> getPermissions(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting permissions for meal plan: {} by user: {}", id, userEmail);
        List<PermissionDto> permissions = mealPlanService.getPermissions(id, userEmail);
        return ResponseEntity.ok(permissions);
    }

    @PostMapping("/{id}/share")
    ResponseEntity<Void> shareMealPlan(
            @PathVariable UUID id,
            @Valid @RequestBody ShareRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Sharing meal plan {} by user {} with {}", id, userEmail, request.email());
        mealPlanService.shareMealPlan(request, id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unshare")
    ResponseEntity<Void> unshareMealPlan(
            @PathVariable UUID id,
            @Valid @RequestBody UnshareRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Unsharing meal plan {} by user {} from {}", id, userEmail, request.email());
        mealPlanService.unshareMealPlan(request.email(), id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generate-shopping-list")
    GeneratedShoppingListResponse generateShoppingListItems(
            @Valid @RequestBody GenerateShoppingListItemsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Generating shopping list items for user: {}", userEmail);
        return mealPlanService.generateShoppingListItems(request.planIds(), request.selectedDates(), userEmail);
    }

    @GetMapping("/calendar")
    Map<LocalDate, List<MealPlanCalendarViewDto>> getCalendarView(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String planIds,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting calendar view for user: {} from {} to {}", userEmail, startDate, endDate);

        List<UUID> planIdList = parsePlanIds(planIds);

        return calendarService.getCalendarView(userEmail, startDate, endDate, planIdList);
    }

    private List<UUID> parsePlanIds(String planIds) {
        if (planIds == null || planIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(planIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();
    }
}

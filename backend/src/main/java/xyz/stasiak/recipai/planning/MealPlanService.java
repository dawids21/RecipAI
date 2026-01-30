package xyz.stasiak.recipai.planning;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.planning.dto.*;
import xyz.stasiak.recipai.planning.exception.*;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class MealPlanService {

    private static final int MAX_OWNED_PLANS = 10;

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanPermissionRepository permissionRepository;
    private final MealPlanEntryRepository entryRepository;

    List<MealPlanDto> findAll(String userEmail) {
        log.debug("Fetching all meal plans for user: {}", userEmail);
        return mealPlanRepository.findAllByUserEmail(userEmail).stream()
                .map(plan -> {
                    MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, plan.getId()))
                            .orElseThrow(() -> new MealPlanAccessDeniedException(plan.getId()));
                    return toDto(plan, permission.getRole());
                })
                .toList();
    }

    @Transactional
    MealPlanDto create(CreateMealPlanRequest request, String userEmail) {
        log.debug("Creating meal plan with name: {} for user: {}", request.name(), userEmail);

        long ownedCount = permissionRepository.countOwnedByEmail(userEmail);
        if (ownedCount >= MAX_OWNED_PLANS) {
            throw new MealPlanLimitExceededException();
        }

        MealPlan mealPlan = new MealPlan(request.name(), request.color());
        MealPlan savedPlan = mealPlanRepository.save(mealPlan);

        MealPlanPermissionId permissionId = new MealPlanPermissionId(userEmail, savedPlan.getId());
        MealPlanPermission permission = new MealPlanPermission(permissionId, UserRole.OWNER);
        permissionRepository.save(permission);

        log.debug("Meal plan created with id: {} for user: {}", savedPlan.getId(), userEmail);
        return toDto(savedPlan, UserRole.OWNER);
    }

    MealPlanDto update(UUID id, UpdateMealPlanRequest request, String userEmail) {
        log.debug("Updating meal plan with id: {} for user: {}", id, userEmail);

        MealPlan mealPlan = mealPlanRepository.findById(id)
                .orElseThrow(() -> new MealPlanNotFoundException(id));

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, id))
                .orElseThrow(() -> new MealPlanAccessDeniedException(id));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(id);
        }

        mealPlan.setName(request.name());
        mealPlan.setColor(request.color());

        MealPlan savedPlan = mealPlanRepository.save(mealPlan);
        return toDto(savedPlan, permission.getRole());
    }

    @Transactional
    void delete(UUID id, String userEmail) {
        log.debug("Deleting meal plan with id: {} for user: {}", id, userEmail);

        if (!mealPlanRepository.existsById(id)) {
            throw new MealPlanNotFoundException(id);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, id))
                .orElseThrow(() -> new MealPlanAccessDeniedException(id));

        if (!permission.hasOwnerRights()) {
            throw new MealPlanAccessDeniedException(id);
        }

        permissionRepository.deleteAllByPlanId(id);
        mealPlanRepository.deleteById(id);
    }

    @Transactional
    MealPlanEntryDto createEntry(UUID planId, CreateMealPlanEntryRequest request, String userEmail) {
        log.debug("Creating entry for meal plan {} by user {}", planId, userEmail);

        if (!mealPlanRepository.existsById(planId)) {
            throw new MealPlanNotFoundException(planId);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, planId))
                .orElseThrow(() -> new MealPlanAccessDeniedException(planId));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(planId);
        }

        validateEntry(request.recipeId(), request.placeholderText(), request.servingSize());

        MealPlanEntry entry = new MealPlanEntry(planId, request.date(), request.recipeId(), request.placeholderText(), request.servingSize());
        MealPlanEntry savedEntry = entryRepository.save(entry);

        return toEntryDto(savedEntry);
    }

    @Transactional
    MealPlanEntryDto updateEntry(UUID planId, Long entryId, UpdateMealPlanEntryRequest request, String userEmail) {
        log.debug("Updating entry {} for meal plan {} by user {}", entryId, planId, userEmail);

        if (!mealPlanRepository.existsById(planId)) {
            throw new MealPlanNotFoundException(planId);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, planId))
                .orElseThrow(() -> new MealPlanAccessDeniedException(planId));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(planId);
        }

        MealPlanEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new MealPlanEntryNotFoundException(entryId));

        if (!entry.getPlanId().equals(planId)) {
            throw new MealPlanEntryNotFoundException(entryId);
        }

        validateEntry(request.recipeId(), request.placeholderText(), request.servingSize());

        entry.setDate(request.date());
        entry.setRecipeId(request.recipeId());
        entry.setPlaceholderText(request.placeholderText());
        entry.setServingSize(request.servingSize());

        MealPlanEntry savedEntry = entryRepository.save(entry);
        return toEntryDto(savedEntry);
    }

    @Transactional
    void deleteEntry(UUID planId, Long entryId, String userEmail) {
        log.debug("Deleting entry {} from meal plan {} by user {}", entryId, planId, userEmail);

        if (!mealPlanRepository.existsById(planId)) {
            throw new MealPlanNotFoundException(planId);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, planId))
                .orElseThrow(() -> new MealPlanAccessDeniedException(planId));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(planId);
        }

        MealPlanEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new MealPlanEntryNotFoundException(entryId));

        if (!entry.getPlanId().equals(planId)) {
            throw new MealPlanEntryNotFoundException(entryId);
        }

        entryRepository.deleteById(entryId);
    }

    private void validateEntry(UUID recipeId, String placeholderText, Integer servingSize) {
        boolean hasRecipe = recipeId != null;
        boolean hasPlaceholder = placeholderText != null && !placeholderText.isBlank();

        if (hasRecipe && hasPlaceholder) {
            throw new InvalidMealPlanEntryException("Entry must have either recipeId or placeholderText, not both");
        }

        if (!hasRecipe && !hasPlaceholder) {
            throw new InvalidMealPlanEntryException("Entry must have either recipeId or placeholderText");
        }

        if (hasRecipe && servingSize == null) {
            throw new InvalidMealPlanEntryException("servingSize is required when recipeId is provided");
        }
    }

    private MealPlanDto toDto(MealPlan plan, UserRole role) {
        return new MealPlanDto(plan.getId(), plan.getName(), plan.getColor(), role, plan.getCreatedAt());
    }

    private MealPlanEntryDto toEntryDto(MealPlanEntry entry) {
        return new MealPlanEntryDto(
                entry.getId(),
                entry.getPlanId(),
                entry.getDate(),
                entry.getRecipeId(),
                entry.getPlaceholderText(),
                entry.getServingSize(),
                entry.getCreatedAt()
        );
    }
}

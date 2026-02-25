package xyz.stasiak.recipai.planning;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import xyz.stasiak.recipai.planning.dto.*;
import xyz.stasiak.recipai.planning.exception.*;
import xyz.stasiak.recipai.provisioning.ProvisioningFacade;
import xyz.stasiak.recipai.provisioning.ProvisioningIngredient;
import xyz.stasiak.recipai.recipes.RecipeDeleted;
import xyz.stasiak.recipai.recipes.RecipeFacade;
import xyz.stasiak.recipai.recipes.RecipeInfo;
import xyz.stasiak.recipai.recipes.RecipeInfoResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class MealPlanService {

    private final MealPlanProperties properties;
    private final MealPlanRepository mealPlanRepository;
    private final MealPlanPermissionRepository permissionRepository;
    private final MealPlanEntryRepository entryRepository;
    private final RecipeFacade recipeFacade;
    private final ProvisioningFacade provisioningFacade;

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
        if (ownedCount >= properties.maxOwnedPlans()) {
            throw new MealPlanLimitExceededException(properties.maxOwnedPlans());
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

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void handleRecipeDeleted(RecipeDeleted event) {
        log.debug("Handling RecipeDeleted event for recipe {} ({})", event.recipeId(), event.recipeName());

        List<MealPlanEntry> entries = entryRepository.findAllByRecipeId(event.recipeId());
        for (MealPlanEntry entry : entries) {
            entry.setPlaceholderText(event.recipeName());
            entry.setRecipeId(null);
            entry.setServingSize(null);
            entryRepository.save(entry);
        }
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

        if (hasPlaceholder && servingSize != null) {
            throw new InvalidMealPlanEntryException("servingSize cannot be provided when placeholderText is provided");
        }
    }

    private MealPlanDto toDto(MealPlan plan, UserRole role) {
        return new MealPlanDto(plan.getId(), plan.getName(), plan.getColor(), role, plan.getCreatedAt());
    }

    GeneratedShoppingListResponse generateShoppingListItems(List<UUID> planIds, List<LocalDate> dates, String userEmail) {
        log.debug("Generating shopping list items for user {} from {} plans on {} dates", userEmail, planIds.size(), dates.size());

        for (UUID planId : planIds) {
            if (!mealPlanRepository.existsById(planId)) {
                throw new MealPlanNotFoundException(planId);
            }
            permissionRepository.findById(new MealPlanPermissionId(userEmail, planId))
                    .orElseThrow(() -> new MealPlanAccessDeniedException(planId));
        }

        List<MealPlanEntry> entries = entryRepository.findEntriesWithRecipes(userEmail, planIds, dates);

        if (entries.isEmpty()) {
            return new GeneratedShoppingListResponse(List.of(), List.of());
        }

        List<UUID> distinctRecipeIds = entries.stream()
                .map(MealPlanEntry::getRecipeId)
                .distinct()
                .toList();

        RecipeInfoResult recipeInfos = recipeFacade.getRecipes(distinctRecipeIds, userEmail);

        Map<UUID, RecipeInfo> recipeMap = recipeInfos.recipes().stream()
                .collect(Collectors.toMap(RecipeInfo::id, Function.identity()));

        List<ProvisioningIngredient> ingredients = entries.stream()
                .filter(entry -> recipeMap.containsKey(entry.getRecipeId()))
                .flatMap(entry -> {
                    RecipeInfo recipe = recipeMap.get(entry.getRecipeId());
                    BigDecimal multiplier = BigDecimal.valueOf(entry.getServingSize())
                            .divide(BigDecimal.valueOf(recipe.servingSize()), 10, RoundingMode.HALF_UP);
                    return recipe.ingredients().stream()
                            .map(ingredient -> new ProvisioningIngredient(
                                    ingredient.name(), ingredient.quantity(), ingredient.unit(), multiplier, recipe.name()));
                })
                .toList();

        List<GeneratedShoppingListItemDto> items = provisioningFacade.provision(ingredients).stream()
                .map(item -> new GeneratedShoppingListItemDto(item.name(), item.quantity(), item.unit(), item.source()))
                .toList();

        return new GeneratedShoppingListResponse(items, recipeInfos.inaccessibleRecipeNames());
    }

    void shareMealPlan(String targetEmail, UUID planId, String requesterEmail) {
        log.debug("Sharing meal plan {} from {} to {}", planId, requesterEmail, targetEmail);

        if (!mealPlanRepository.existsById(planId)) {
            throw new MealPlanNotFoundException(planId);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(requesterEmail, planId))
                .orElseThrow(() -> new MealPlanAccessDeniedException(planId));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(planId);
        }

        MealPlanPermissionId targetPermissionId = new MealPlanPermissionId(targetEmail, planId);
        if (permissionRepository.findById(targetPermissionId).isPresent()) {
            log.warn("Meal plan {} is already shared with user {}", planId, targetEmail);
            return;
        }

        MealPlanPermission newPermission = new MealPlanPermission(targetPermissionId, UserRole.EDITOR);
        permissionRepository.save(newPermission);

        log.info("Meal plan {} shared from {} to {}", planId, requesterEmail, targetEmail);
    }

    void unshareMealPlan(String targetEmail, UUID planId, String requesterEmail) {
        log.debug("Unsharing meal plan {} from {} for {}", planId, requesterEmail, targetEmail);

        if (!mealPlanRepository.existsById(planId)) {
            throw new MealPlanNotFoundException(planId);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(requesterEmail, planId))
                .orElseThrow(() -> new MealPlanAccessDeniedException(planId));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(planId);
        }

        MealPlanPermissionId targetPermissionId = new MealPlanPermissionId(targetEmail, planId);
        MealPlanPermission targetPermission = permissionRepository.findById(targetPermissionId)
                .orElse(null);

        if (targetPermission != null && targetPermission.hasOwnerRights()) {
            if (targetEmail.equals(requesterEmail)) {
                log.warn("OWNER {} cannot unshare themselves from meal plan {}", requesterEmail, planId);
            } else {
                log.warn("Cannot unshare OWNER {} from meal plan {}", targetEmail, planId);
            }
            throw new MealPlanAccessDeniedException(planId);
        }

        permissionRepository.deleteById(targetPermissionId);

        log.info("Meal plan {} unshared from {} for {}", planId, requesterEmail, targetEmail);
    }

    List<SharedUserDto> getSharedUsers(UUID planId, String userEmail) {
        log.debug("Getting shared users for meal plan: {} by user: {}", planId, userEmail);

        if (!mealPlanRepository.existsById(planId)) {
            throw new MealPlanNotFoundException(planId);
        }

        MealPlanPermission permission = permissionRepository.findById(new MealPlanPermissionId(userEmail, planId))
                .orElseThrow(() -> new MealPlanAccessDeniedException(planId));

        if (!permission.hasEditorRights()) {
            throw new MealPlanAccessDeniedException(planId);
        }

        return permissionRepository.findAllByPlanId(planId).stream()
                .map(perm -> new SharedUserDto(perm.getId().email(), perm.getRole()))
                .toList();
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

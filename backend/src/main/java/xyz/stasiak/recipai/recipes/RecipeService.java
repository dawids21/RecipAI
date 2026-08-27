package xyz.stasiak.recipai.recipes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.PermissionsFacade;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.permissions.exception.ResourceAccessDeniedException;
import xyz.stasiak.recipai.recipes.collections.RecipesCollectionService;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionUnshared;
import xyz.stasiak.recipai.recipes.images.RecipeImagesService;
import xyz.stasiak.recipai.recipes.images.dto.RecipeImageDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class RecipeService {

    static final String RECIPE_RESOURCE = "RECIPE";

    private final RecipeRepository recipeRepository;
    private final PermissionsFacade permissionsFacade;
    private final ObjectMapper objectMapper;
    private final RecipesCollectionService recipesCollectionService;
    private final RecipeImagesService recipeImagesService;
    private final ApplicationEventPublisher eventPublisher;
    private final LimitsFacade limitsFacade;

    public LimitBalance balance(String userEmail) {
        log.debug("Getting recipe balance for user {}", userEmail);
        return limitsFacade.getBalance(userEmail, RECIPE_RESOURCE).orElse(LimitBalance.zero());
    }

    public List<RecipeListDto> findAll(String userEmail) {
        log.debug("Finding all accessible recipes for user {}", userEmail);

        // No empty-set short-circuit here (unlike findAllUnassigned): a collection-derived reader has
        // no direct recipe permission, so an empty recipe id set must still reach the query's
        // collection-membership OR branch instead of skipping it. This relies on Hibernate 6
        // rendering an empty `IN` list as a false predicate rather than invalid SQL.
        Set<UUID> recipeIds = permissionsFacade.accessibleResources(RECIPE_RESOURCE, userEmail).keySet();
        Set<UUID> collectionIds = recipesCollectionService.accessibleCollectionIds(userEmail);

        return recipeRepository.findAllByUserEmail(recipeIds, collectionIds).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public List<RecipeListDto> findAllByCollectionId(UUID collectionId, String userEmail) {
        log.debug("Finding recipes in collection {} for user {}", collectionId, userEmail);

        // Validate user has access to the collection (throws RecipesCollectionNotFoundException or ResourceAccessDeniedException)
        recipesCollectionService.findById(collectionId, userEmail);

        // If validation passes, fetch recipes in this collection
        return recipeRepository.findAllByRecipesCollectionIdOrderByCreatedAt(collectionId).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public List<RecipeListDto> findAllUnassigned(String userEmail) {
        log.debug("Finding unassigned recipes for user {}", userEmail);

        Set<UUID> recipeIds = permissionsFacade.accessibleResources(RECIPE_RESOURCE, userEmail).keySet();
        if (recipeIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> collectionIds = recipesCollectionService.accessibleCollectionIds(userEmail);

        return recipeRepository.findAllUnassignedByUserEmail(recipeIds, collectionIds).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public RecipeDetailsDto findById(UUID id, String userEmail) {
        log.debug("Finding recipe with id: {} for user: {}", id, userEmail);

        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        ResourceRole role = resolveAccess(userEmail, recipe);

        String collectionName = null;
        if (recipe.getRecipesCollectionId() != null) {
            try {
                RecipesCollectionListDto collectionDto = recipesCollectionService.findById(recipe.getRecipesCollectionId(), userEmail);
                collectionName = collectionDto.name();
            } catch (ResourceAccessDeniedException _) {
                // we don't show collection name if user has no access
            }
        }

        List<RecipeImageDto> images = recipeImagesService.findImagesById(id);

        return toDetailsDto(recipe, role, collectionName, images);
    }

    @Transactional
    public RecipeDetailsDto save(CreateRecipeRequest request, String userEmail) {
        return save(request, null, userEmail);
    }

    @Transactional
    public RecipeDetailsDto save(CreateRecipeRequest request, List<MultipartFile> images, String userEmail) {
        limitsFacade.reserve(userEmail, RECIPE_RESOURCE);

        log.debug("Creating recipe with name: {} for user: {}", request.name(), userEmail);

        Recipe recipe = new Recipe();
        recipe.setName(request.name());
        recipe.setData(convertToJsonNode(request.data()));
        recipe.setRecipesCollectionId(request.recipesCollectionId());

        // Validate collection if provided
        RecipesCollectionListDto collectionDto = null;
        if (request.recipesCollectionId() != null) {
            collectionDto = recipesCollectionService.findById(request.recipesCollectionId(), userEmail);
            recipe.setRecipesCollectionId(collectionDto.id());
            log.debug("Recipe will be assigned to collection: {}", request.recipesCollectionId());
        }

        Recipe savedRecipe = recipeRepository.save(recipe);

        permissionsFacade.grantOwner(RECIPE_RESOURCE, savedRecipe.getId(), userEmail);

        log.info("Recipe created with id: {}", savedRecipe.getId());

        recipeImagesService.createEmptyRecipeImages(savedRecipe.getId());

        if (request.images() != null && !request.images().isEmpty()) {
            recipeImagesService.uploadImages(savedRecipe.getId(), request.images(), images);
        }

        List<RecipeImageDto> recipeImages = recipeImagesService.findImagesById(savedRecipe.getId());
        return toDetailsDto(savedRecipe, ResourceRole.OWNER, collectionDto != null ? collectionDto.name() : null, recipeImages);
    }

    @Transactional
    public RecipeDetailsDto updateById(UUID id, UpdateRecipeRequest request, String userEmail) {
        return updateById(id, request, List.of(), userEmail);
    }

    @Transactional
    public RecipeDetailsDto updateById(UUID id, UpdateRecipeRequest request, List<MultipartFile> images, String userEmail) {
        log.debug("Updating recipe with id: {} and {} images for user: {}", id, images.size(), userEmail);

        Recipe existingRecipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        ResourceRole userRole = resolveAccess(userEmail, existingRecipe);

        // Validate collection if provided
        if (request.recipesCollectionId() != null && !request.recipesCollectionId().equals(existingRecipe.getRecipesCollectionId())) {
            recipesCollectionService.findById(request.recipesCollectionId(), userEmail);
        }

        existingRecipe.setName(request.name());
        existingRecipe.setData(convertToJsonNode(request.data()));
        if (userRole == ResourceRole.OWNER) {
            existingRecipe.setRecipesCollectionId(request.recipesCollectionId());
        }

        Recipe savedRecipe = recipeRepository.save(existingRecipe);

        String collectionName = null;
        if (savedRecipe.getRecipesCollectionId() != null) {
            try {
                RecipesCollectionListDto collectionDto = recipesCollectionService.findById(savedRecipe.getRecipesCollectionId(), userEmail);
                collectionName = collectionDto.name();
            } catch (ResourceAccessDeniedException _) {
                // we don't show collection name if user has no access
            }
        }

        if (request.images() != null) {
            recipeImagesService.uploadImages(savedRecipe.getId(), request.images(), images);
        }

        log.info("Recipe updated with id: {}", savedRecipe.getId());

        List<RecipeImageDto> recipeImages = recipeImagesService.findImagesById(savedRecipe.getId());
        return toDetailsDto(savedRecipe, userRole, collectionName, recipeImages);
    }

    @Transactional
    public void deleteById(UUID id, String userEmail) {
        log.debug("Deleting recipe with id: {} for user: {}", id, userEmail);

        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        ResourceRole userRole = resolveAccess(userEmail, recipe);

        if (userRole != ResourceRole.OWNER) {
            throw new ResourceAccessDeniedException(RECIPE_RESOURCE, id);
        }

        eventPublisher.publishEvent(new RecipeDeleted(recipe.getId(), recipe.getName()));

        log.debug("Clearing permissions and pending invites for recipe {}", id);
        permissionsFacade.resourceDeleted(RECIPE_RESOURCE, id);

        recipeRepository.deleteById(id);

        recipeImagesService.deleteAllImages(id);

        limitsFacade.release(userEmail, RECIPE_RESOURCE);
    }

    private RecipeDetailsDto toDetailsDto(Recipe recipe, ResourceRole role, String collectionName, List<RecipeImageDto> images) {
        RecipeData recipeData = convertToRecipeData(recipe.getData());
        return new RecipeDetailsDto(recipe.getId(), recipe.getName(), recipeData, role, recipe.getRecipesCollectionId(), collectionName, images);
    }

    private RecipeListDto toRecipeListDto(Recipe recipe) {
        String thumbnailUrl = recipeImagesService.getFirstThumbnailUrl(recipe.getId());
        return new RecipeListDto(recipe.getId(), recipe.getName(), thumbnailUrl);
    }

    private RecipeData convertToRecipeData(JsonNode jsonNode) {
        try {
            List<Ingredient> ingredients;
            List<Instruction> instructions;
            String sourceUrl = null;
            int servingSize = 1;

            if (jsonNode.has("ingredients")) {
                ingredients = objectMapper.treeToValue(jsonNode.get("ingredients"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Ingredient.class));
            } else {
                ingredients = List.of();
            }

            if (jsonNode.has("instructions")) {
                instructions = objectMapper.treeToValue(jsonNode.get("instructions"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Instruction.class)
                );
            } else {
                instructions = List.of();
            }

            if (jsonNode.has("sourceUrl") && !jsonNode.get("sourceUrl").isNull()) {
                sourceUrl = jsonNode.get("sourceUrl").asString();
            }

            if (jsonNode.has("servingSize") && !jsonNode.get("servingSize").isNull()) {
                servingSize = jsonNode.get("servingSize").asInt();
            }

            return new RecipeData(ingredients, instructions, sourceUrl, servingSize);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to RecipeData", e);
            throw new RuntimeException("Invalid recipe data format", e);
        }
    }

    private JsonNode convertToJsonNode(RecipeData recipeData) {
        return objectMapper.valueToTree(recipeData);
    }

    public void shareRecipe(ShareRequest request, UUID recipeId, String requesterEmail) {
        log.debug("Sharing recipe {} from {} to {}", recipeId, requesterEmail, request.email());

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        resolveAccess(requesterEmail, recipe);

        permissionsFacade.invite(RECIPE_RESOURCE, recipeId, request.email(), request.role(), recipe.getName(), requesterEmail);

        log.info("Recipe {} invite created from {} to {}", recipeId, requesterEmail, request.email());
    }

    public void unshareRecipe(String targetEmail, UUID recipeId, String requesterEmail) {
        log.debug("Unsharing recipe {} from {} for {}", recipeId, requesterEmail, targetEmail);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        resolveAccess(requesterEmail, recipe);

        permissionsFacade.revoke(RECIPE_RESOURCE, recipeId, targetEmail, requesterEmail);

        log.info("Recipe {} unshared successfully from {} for {}", recipeId, requesterEmail, targetEmail);
    }

    public List<PermissionDto> getPermissions(UUID recipeId, String userEmail) {
        log.debug("Getting permissions for recipe: {} by user: {}", recipeId, userEmail);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        resolveAccess(userEmail, recipe);

        return permissionsFacade.getPermissions(RECIPE_RESOURCE, recipeId);
    }

    private ResourceRole resolveAccess(String userEmail, Recipe recipe) {
        Optional<ResourceRole> direct = permissionsFacade.roleOf(RECIPE_RESOURCE, recipe.getId(), userEmail);
        if (direct.isPresent()) {
            return direct.get();
        }

        if (recipe.getRecipesCollectionId() != null
                && recipesCollectionService.roleOf(recipe.getRecipesCollectionId(), userEmail).isPresent()) {
            log.debug("User {} has access to recipe {} via collection {}", userEmail, recipe.getId(), recipe.getRecipesCollectionId());
            return ResourceRole.EDITOR;
        }

        throw new ResourceAccessDeniedException(RECIPE_RESOURCE, recipe.getId());
    }

    Set<UUID> accessibleRecipeIds(String userEmail) {
        // Same composition findAll performs, ids only: a direct permission or membership of an
        // accessible collection. Neither set short-circuits — a collection-derived reader holds no
        // direct recipe permission, so an empty recipe id set must still reach the query's
        // collection-membership OR branch.
        Set<UUID> recipeIds = permissionsFacade.accessibleResources(RECIPE_RESOURCE, userEmail).keySet();
        Set<UUID> collectionIds = recipesCollectionService.accessibleCollectionIds(userEmail);

        return recipeRepository.findAccessibleIds(recipeIds, collectionIds);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void handleRecipesCollectionUnshared(RecipesCollectionUnshared event) {
        log.debug("Handling RecipesCollectionUnshared event for collection {} and user {}",
                event.recipesCollectionId(), event.userEmail());

        Map<UUID, ResourceRole> owned = permissionsFacade.accessibleResources(RECIPE_RESOURCE, event.userEmail());

        List<Recipe> recipes = recipeRepository.findAllByRecipesCollectionIdOrderByCreatedAt(event.recipesCollectionId());

        for (Recipe recipe : recipes) {
            if (owned.get(recipe.getId()) == ResourceRole.OWNER) {
                recipe.setRecipesCollectionId(null);
                recipeRepository.save(recipe);
            }
        }
    }
}

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
import xyz.stasiak.recipai.recipes.collections.RecipesCollectionService;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionUnshared;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionAccessDeniedException;
import xyz.stasiak.recipai.recipes.images.RecipeImagesService;
import xyz.stasiak.recipai.recipes.images.dto.RecipeImageDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipePermissionRepository recipePermissionRepository;
    private final ObjectMapper objectMapper;
    private final RecipesCollectionService recipesCollectionService;
    private final RecipeImagesService recipeImagesService;
    private final ApplicationEventPublisher eventPublisher;

    public List<RecipeListDto> findAll(String userEmail) {
        log.debug("Finding all accessible recipes for user {}", userEmail);

        return recipeRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public List<RecipeListDto> findAllByCollectionId(UUID collectionId, String userEmail) {
        log.debug("Finding recipes in collection {} for user {}", collectionId, userEmail);

        // Validate user has access to the collection (throws RecipesCollectionNotFoundException or RecipesCollectionAccessDeniedException)
        recipesCollectionService.findById(collectionId, userEmail);

        // If validation passes, fetch recipes in this collection
        return recipeRepository.findAllByRecipesCollectionIdOrderByCreatedAt(collectionId).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public List<RecipeListDto> findAllUnassigned(String userEmail) {
        log.debug("Finding unassigned recipes for user {}", userEmail);

        return recipeRepository.findAllUnassignedByUserEmail(userEmail).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public RecipeDetailsDto findById(UUID id, String userEmail) {
        log.debug("Finding recipe with id: {} for user: {}", id, userEmail);

        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        UserRole userRole = validateRecipeAccess(userEmail, recipe);

        String collectionName = null;
        if (recipe.getRecipesCollectionId() != null) {
            try {
                RecipesCollectionListDto collectionDto = recipesCollectionService.findById(recipe.getRecipesCollectionId(), userEmail);
                collectionName = collectionDto.name();
            } catch (RecipesCollectionAccessDeniedException _) {
                // we don't show collection name if user has no access
            }
        }

        List<RecipeImageDto> images = recipeImagesService.findImagesById(id);

        return toDetailsDto(recipe, userRole, collectionName, images);
    }

    @Transactional
    public RecipeDetailsDto save(CreateRecipeRequest request, String userEmail) {
        return save(request, null, userEmail);
    }

    @Transactional
    public RecipeDetailsDto save(CreateRecipeRequest request, List<MultipartFile> images, String userEmail) {
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

        // Create RecipePermission association with OWNER role
        RecipePermission recipePermission = new RecipePermission();
        RecipePermissionId recipePermissionId = new RecipePermissionId(userEmail, savedRecipe.getId());
        recipePermission.setId(recipePermissionId);
        recipePermission.setRole(UserRole.OWNER);
        recipePermissionRepository.save(recipePermission);

        log.info("Recipe created with id: {}", savedRecipe.getId());

        recipeImagesService.createEmptyRecipeImages(savedRecipe.getId());

        if (request.images() != null && !request.images().isEmpty()) {
            recipeImagesService.uploadImages(savedRecipe.getId(), request.images(), images);
        }

        List<RecipeImageDto> recipeImages = recipeImagesService.findImagesById(savedRecipe.getId());
        return toDetailsDto(savedRecipe, UserRole.OWNER, collectionDto != null ? collectionDto.name() : null, recipeImages);
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

        UserRole userRole = validateRecipeAccess(userEmail, existingRecipe);

        // Both OWNER and EDITOR can update recipes
        if (userRole != UserRole.OWNER && userRole != UserRole.EDITOR) {
            throw new RecipeAccessDeniedException(id);
        }

        // Validate collection if provided
        if (request.recipesCollectionId() != null && existingRecipe.getRecipesCollectionId() != request.recipesCollectionId()) {
            recipesCollectionService.findById(request.recipesCollectionId(), userEmail);
        }

        existingRecipe.setName(request.name());
        existingRecipe.setData(convertToJsonNode(request.data()));
        if (userRole == UserRole.OWNER) {
            existingRecipe.setRecipesCollectionId(request.recipesCollectionId());
        }

        Recipe savedRecipe = recipeRepository.save(existingRecipe);

        String collectionName = null;
        if (savedRecipe.getRecipesCollectionId() != null) {
            try {
                RecipesCollectionListDto collectionDto = recipesCollectionService.findById(savedRecipe.getRecipesCollectionId(), userEmail);
                collectionName = collectionDto.name();
            } catch (RecipesCollectionAccessDeniedException _) {
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

        UserRole userRole = validateRecipeAccess(userEmail, recipe);

        if (userRole != UserRole.OWNER) {
            throw new RecipeAccessDeniedException(id);
        }

        eventPublisher.publishEvent(new RecipeDeleted(recipe.getId(), recipe.getName()));

        // Delete ALL RecipePermission associations first (including shared users)
        recipePermissionRepository.deleteAllByRecipeId(id);

        // Then delete the recipe itself
        recipeRepository.deleteById(id);

        recipeImagesService.deleteAllImages(id);
    }

    private RecipeDetailsDto toDetailsDto(Recipe recipe, UserRole userRole, String collectionName, List<RecipeImageDto> images) {
        RecipeData recipeData = convertToRecipeData(recipe.getData());
        return new RecipeDetailsDto(recipe.getId(), recipe.getName(), recipeData, userRole, recipe.getRecipesCollectionId(), collectionName, images);
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

    public void shareRecipe(String targetEmail, UUID recipeId, String requesterEmail) {
        log.debug("Sharing recipe {} from {} to {}", recipeId, requesterEmail, targetEmail);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        validateRecipeAccess(requesterEmail, recipe);

        // Check if target user already has access
        if (recipePermissionRepository.getUserRole(targetEmail, recipeId).isPresent()) {
            log.warn("Recipe {} is already shared with user {}", recipeId, targetEmail);
            return; // Already shared, no-op
        }

        // Create EDITOR association for target user
        RecipePermission sharedRecipe = new RecipePermission();
        RecipePermissionId sharedRecipeId = new RecipePermissionId(targetEmail, recipeId);
        sharedRecipe.setId(sharedRecipeId);
        sharedRecipe.setRole(UserRole.EDITOR);
        recipePermissionRepository.save(sharedRecipe);

        log.info("Recipe {} shared successfully from {} to {}", recipeId, requesterEmail, targetEmail);
    }

    public void unshareRecipe(String targetEmail, UUID recipeId, String requesterEmail) {
        log.debug("Unsharing recipe {} from {} for {}", recipeId, requesterEmail, targetEmail);

        if (targetEmail.equals(requesterEmail)) {
            log.warn("User {} cannot unshare themselves from recipe {}", requesterEmail, recipeId);
            throw new IllegalArgumentException("Cannot unshare yourself from a recipe");
        }

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        validateRecipeAccess(requesterEmail, recipe);

        // Get target user's role - validate they have access and prevent unsharing OWNER
        UserRole targetRole = recipePermissionRepository.getUserRole(targetEmail, recipeId)
                .orElse(null);

        if (targetRole == null) {
            log.warn("User {} does not have access to recipe {} or recipe doesn't exist", targetEmail, recipeId);
            return; // No access to remove, no-op
        }

        if (targetRole == UserRole.OWNER) {
            log.warn("Cannot unshare OWNER {} from recipe {}", targetEmail, recipeId);
            throw new RecipeAccessDeniedException(recipeId);
        }

        // Remove the EDITOR association
        RecipePermissionId targetRecipePermissionId = new RecipePermissionId(targetEmail, recipeId);
        recipePermissionRepository.deleteById(targetRecipePermissionId);

        log.info("Recipe {} unshared successfully from {} for {}", recipeId, requesterEmail, targetEmail);
    }

    public List<SharedUserDto> getSharedUsers(UUID recipeId, String userEmail) {
        log.debug("Getting shared users for recipe: {} by user: {}", recipeId, userEmail);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        validateRecipeAccess(userEmail, recipe);

        // Get all users with access to this recipe (OWNER first due to ORDER BY role DESC)
        return recipePermissionRepository.findAllByRecipeId(recipeId).stream()
                .map(recipePermission -> new SharedUserDto(recipePermission.getId().email(), recipePermission.getRole()))
                .toList();
    }

    private UserRole validateRecipeAccess(String userEmail, Recipe recipe) {
        Optional<UserRole> directRole = recipePermissionRepository.getUserRole(userEmail, recipe.getId());
        if (directRole.isPresent()) {
            return directRole.get();
        }

        if (recipe.getRecipesCollectionId() != null) {
            try {
                recipesCollectionService.findById(recipe.getRecipesCollectionId(), userEmail);
                log.debug("User {} has access to recipe {} via collection {}", userEmail, recipe.getId(), recipe.getRecipesCollectionId());
                return UserRole.EDITOR;
            } catch (Exception e) {
                log.debug("User {} does not have access to collection {} for recipe {}", userEmail, recipe.getRecipesCollectionId(), recipe.getId());
            }
        }

        throw new RecipeAccessDeniedException(recipe.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void handleRecipesCollectionUnshared(RecipesCollectionUnshared event) {
        log.debug("Handling RecipesCollectionUnshared event for collection {} and user {}",
                event.recipesCollectionId(), event.userEmail());

        List<Recipe> recipes = recipeRepository.findAllByRecipesCollectionIdOrderByCreatedAt(event.recipesCollectionId());

        for (Recipe recipe : recipes) {
            Optional<UserRole> role = recipePermissionRepository.getUserRole(event.userEmail(), recipe.getId());
            if (role.isPresent() && role.get() == UserRole.OWNER) {
                recipe.setRecipesCollectionId(null);
                recipeRepository.save(recipe);
            }
        }
    }
}
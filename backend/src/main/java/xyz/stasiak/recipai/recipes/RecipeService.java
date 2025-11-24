package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipePermissionRepository recipePermissionRepository;
    private final ObjectMapper objectMapper;

    public List<RecipeListDto> findAll(String userEmail) {
        return recipeRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public RecipeDto findById(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        // Check if user has access and get their role
        UserRole userRole = recipePermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new RecipeAccessDeniedException(id));

        return toDto(recipe, userRole);
    }

    public RecipeDto save(CreateRecipeRequest request, String userEmail) {
        Recipe recipe = new Recipe();
        recipe.setName(request.name());
        recipe.setData(convertToJsonNode(request.data()));

        Recipe savedRecipe = recipeRepository.save(recipe);

        // Create RecipePermission association with OWNER role
        RecipePermission recipePermission = new RecipePermission();
        RecipePermissionId recipePermissionId = new RecipePermissionId(userEmail, savedRecipe.getId());
        recipePermission.setId(recipePermissionId);
        recipePermission.setRole(UserRole.OWNER);
        recipePermissionRepository.save(recipePermission);

        return toDto(savedRecipe, UserRole.OWNER);
    }

    public RecipeDto updateById(UUID id, UpdateRecipeRequest request, String userEmail) {
        log.debug("Updating recipe with id: {} for user: {}", id, userEmail);

        Recipe existingRecipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        // Check if user has access (OWNER or EDITOR) and get their role
        UserRole userRole = recipePermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new RecipeAccessDeniedException(id));

        // Both OWNER and EDITOR can update recipes
        if (userRole != UserRole.OWNER && userRole != UserRole.EDITOR) {
            throw new RecipeAccessDeniedException(id);
        }

        existingRecipe.setName(request.name());
        existingRecipe.setData(convertToJsonNode(request.data()));

        Recipe savedRecipe = recipeRepository.save(existingRecipe);
        return toDto(savedRecipe, userRole);
    }

    public void deleteById(UUID id, String userEmail) {
        log.debug("Deleting recipe with id: {} for user: {}", id, userEmail);

        if (!recipeRepository.existsById(id)) {
            throw new RecipeNotFoundException(id);
        }

        // Only OWNER can delete recipes
        UserRole userRole = recipePermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new RecipeAccessDeniedException(id));

        if (userRole != UserRole.OWNER) {
            throw new RecipeAccessDeniedException(id);
        }

        // Delete ALL RecipePermission associations first (including shared users)
        recipePermissionRepository.deleteAllByRecipeId(id);

        // Then delete the recipe itself
        recipeRepository.deleteById(id);
    }

    private RecipeDto toDto(Recipe recipe, UserRole userRole) {
        RecipeData recipeData = convertToRecipeData(recipe.getData());
        return new RecipeDto(recipe.getId(), recipe.getName(), recipeData, userRole);
    }

    private RecipeListDto toRecipeListDto(Recipe recipe) {
        return new RecipeListDto(recipe.getId(), recipe.getName());
    }

    private RecipeData convertToRecipeData(JsonNode jsonNode) {
        try {
            List<Ingredient> ingredients;
            List<Instruction> instructions;

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

            return new RecipeData(ingredients, instructions);
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

        // Validate recipe exists
        if (!recipeRepository.existsById(recipeId)) {
            throw new RecipeNotFoundException(recipeId);
        }

        // Validate that the requester has access (OWNER or EDITOR can share)
        recipePermissionRepository.getUserRole(requesterEmail, recipeId)
                .orElseThrow(() -> new RecipeAccessDeniedException(recipeId));

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

        // Validate recipe exists
        if (!recipeRepository.existsById(recipeId)) {
            throw new RecipeNotFoundException(recipeId);
        }

        // Validate that the requester has access (OWNER or EDITOR can unshare)
        recipePermissionRepository.getUserRole(requesterEmail, recipeId)
                .orElseThrow(() -> new RecipeAccessDeniedException(recipeId));

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

        // Validate recipe exists
        if (!recipeRepository.existsById(recipeId)) {
            throw new RecipeNotFoundException(recipeId);
        }

        // Validate user has access
        recipePermissionRepository.getUserRole(userEmail, recipeId)
                .orElseThrow(() -> new RecipeAccessDeniedException(recipeId));

        // Get all users with access to this recipe (OWNER first due to ORDER BY role DESC)
        return recipePermissionRepository.findAllByRecipeId(recipeId).stream()
                .map(recipePermission -> new SharedUserDto(recipePermission.getId().email(), recipePermission.getRole()))
                .toList();
    }
}
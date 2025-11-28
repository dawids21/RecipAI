package xyz.stasiak.recipai.recipes.collections;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.recipes.collections.dto.UpdateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionAccessDeniedException;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class RecipesCollectionService {

    private final RecipesCollectionRepository recipesCollectionRepository;
    private final RecipesCollectionPermissionRepository permissionRepository;

    List<RecipesCollectionListDto> findAll(String userEmail) {
        log.debug("Fetching all recipes collections for user: {}", userEmail);
        return recipesCollectionRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toListDto)
                .toList();
    }

    @Transactional
    RecipesCollectionListDto create(CreateRecipesCollectionRequest request, String userEmail) {
        log.debug("Creating recipes collection with name: {} for user: {}", request.name(), userEmail);

        RecipesCollection recipesCollection = new RecipesCollection(request.name());
        RecipesCollection savedCollection = recipesCollectionRepository.save(recipesCollection);

        RecipesCollectionPermissionId permissionId = new RecipesCollectionPermissionId(userEmail, savedCollection.getId());
        RecipesCollectionPermission permission = new RecipesCollectionPermission(permissionId, UserRole.OWNER);
        permissionRepository.save(permission);

        log.debug("Recipes collection created with id: {} for user: {}", savedCollection.getId(), userEmail);
        return toListDto(savedCollection);
    }

    RecipesCollectionListDto updateById(UUID id, UpdateRecipesCollectionRequest request, String userEmail) {
        log.debug("Updating recipes collection with id: {} for user: {}", id, userEmail);

        RecipesCollection recipesCollection = recipesCollectionRepository.findById(id)
                .orElseThrow(() -> new RecipesCollectionNotFoundException(id));

        RecipesCollectionPermission permission = permissionRepository.findById(new RecipesCollectionPermissionId(userEmail, id))
                .orElseThrow(() -> new RecipesCollectionAccessDeniedException(id));

        if (!permission.hasEditorRights()) {
            throw new RecipesCollectionAccessDeniedException(id);
        }

        recipesCollection.setName(request.name());

        RecipesCollection savedCollection = recipesCollectionRepository.save(recipesCollection);

        return toListDto(savedCollection);
    }

    @Transactional
    void deleteById(UUID id, String userEmail) {
        log.debug("Deleting recipes collection with id: {} for user: {}", id, userEmail);

        if (!recipesCollectionRepository.existsById(id)) {
            throw new RecipesCollectionNotFoundException(id);
        }

        RecipesCollectionPermission permission = permissionRepository.findById(new RecipesCollectionPermissionId(userEmail, id))
                .orElseThrow(() -> new RecipesCollectionAccessDeniedException(id));

        if (!permission.hasOwnerRights()) {
            throw new RecipesCollectionAccessDeniedException(id);
        }

        log.debug("Deleting all permissions for recipes collection {}", id);
        permissionRepository.deleteAllByRecipesCollectionId(id);

        recipesCollectionRepository.deleteById(id);
    }

    private RecipesCollectionListDto toListDto(RecipesCollection recipesCollection) {
        return new RecipesCollectionListDto(recipesCollection.getId(), recipesCollection.getName());
    }
}
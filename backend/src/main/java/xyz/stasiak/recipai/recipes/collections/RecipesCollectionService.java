package xyz.stasiak.recipai.recipes.collections;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.recipes.collections.dto.*;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionAccessDeniedException;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipesCollectionService {

    private final RecipesCollectionRepository recipesCollectionRepository;
    private final RecipesCollectionPermissionRepository permissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    List<RecipesCollectionListDto> findAll(String userEmail) {
        log.debug("Fetching all recipes collections for user: {}", userEmail);
        return recipesCollectionRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toListDto)
                .toList();
    }

    public RecipesCollectionListDto findById(UUID collectionId, String userEmail) {
        log.debug("Finding collection for id: {} by user: {}", collectionId, userEmail);

        RecipesCollection collection = recipesCollectionRepository.findById(collectionId)
                .orElseThrow(() -> new RecipesCollectionNotFoundException(collectionId));

        // Validate user has permission (at least EDITOR role)
        RecipesCollectionPermission permission = permissionRepository.findById(
                        new RecipesCollectionPermissionId(userEmail, collectionId))
                .orElseThrow(() -> new RecipesCollectionAccessDeniedException(collectionId));

        if (!permission.hasEditorRights()) {
            throw new RecipesCollectionAccessDeniedException(collectionId);
        }

        return toListDto(collection);
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

    void shareRecipesCollection(String targetEmail, UUID recipesCollectionId, String requesterEmail) {
        log.debug("Sharing recipes collection {} from {} to {}", recipesCollectionId, requesterEmail, targetEmail);

        // Validate recipes collection exists
        if (!recipesCollectionRepository.existsById(recipesCollectionId)) {
            throw new RecipesCollectionNotFoundException(recipesCollectionId);
        }

        // Validate requester has access (OWNER or EDITOR can share)
        permissionRepository.findById(new RecipesCollectionPermissionId(requesterEmail, recipesCollectionId))
                .orElseThrow(() -> new RecipesCollectionAccessDeniedException(recipesCollectionId));

        // Check if target already has access - no-op if already shared
        RecipesCollectionPermissionId targetPermissionId = new RecipesCollectionPermissionId(targetEmail, recipesCollectionId);
        if (permissionRepository.findById(targetPermissionId).isPresent()) {
            log.warn("Recipes collection {} is already shared with user {}", recipesCollectionId, targetEmail);
            return;
        }

        // Create EDITOR permission for target user
        RecipesCollectionPermission permission = new RecipesCollectionPermission(targetPermissionId, UserRole.EDITOR);
        permissionRepository.save(permission);

        log.info("Recipes collection {} shared from {} to {}", recipesCollectionId, requesterEmail, targetEmail);
    }

    @Transactional
    void unshareRecipesCollection(String targetEmail, UUID recipesCollectionId, String requesterEmail) {
        log.debug("Unsharing recipes collection {} from {} for {}", recipesCollectionId, requesterEmail, targetEmail);

        // Validate recipes collection exists
        if (!recipesCollectionRepository.existsById(recipesCollectionId)) {
            throw new RecipesCollectionNotFoundException(recipesCollectionId);
        }

        // Validate requester has access
        permissionRepository.findById(new RecipesCollectionPermissionId(requesterEmail, recipesCollectionId))
                .orElseThrow(() -> new RecipesCollectionAccessDeniedException(recipesCollectionId));

        // Get target user's permission
        RecipesCollectionPermissionId targetPermissionId = new RecipesCollectionPermissionId(targetEmail, recipesCollectionId);
        RecipesCollectionPermission targetPermission = permissionRepository.findById(targetPermissionId)
                .orElse(null);

        // Prevent unsharing OWNER
        if (targetPermission != null && targetPermission.hasOwnerRights()) {
            if (targetEmail.equals(requesterEmail)) {
                log.warn("OWNER {} cannot unshare themselves from recipes collection {}", requesterEmail, recipesCollectionId);
            } else {
                log.warn("Cannot unshare OWNER {} from recipes collection {}", targetEmail, recipesCollectionId);
            }
            throw new RecipesCollectionAccessDeniedException(recipesCollectionId);
        }

        // Remove EDITOR permission (deleteById is no-op if record doesn't exist)
        permissionRepository.deleteById(targetPermissionId);

        eventPublisher.publishEvent(new RecipesCollectionUnshared(recipesCollectionId, targetEmail));

        log.info("Recipes collection {} unshared from {} for {}", recipesCollectionId, requesterEmail, targetEmail);
    }

    List<SharedUserDto> getSharedUsers(UUID recipesCollectionId, String userEmail) {
        log.debug("Getting shared users for recipes collection: {} by user: {}", recipesCollectionId, userEmail);

        // Validate recipes collection exists
        if (!recipesCollectionRepository.existsById(recipesCollectionId)) {
            throw new RecipesCollectionNotFoundException(recipesCollectionId);
        }

        // Validate user has access
        permissionRepository.findById(new RecipesCollectionPermissionId(userEmail, recipesCollectionId))
                .orElseThrow(() -> new RecipesCollectionAccessDeniedException(recipesCollectionId));

        // Return all users with access, OWNER first
        return permissionRepository.findAllByRecipesCollectionId(recipesCollectionId).stream()
                .map(perm -> new SharedUserDto(perm.getId().email(), perm.getRole()))
                .toList();
    }

    private RecipesCollectionListDto toListDto(RecipesCollection recipesCollection) {
        return new RecipesCollectionListDto(recipesCollection.getId(), recipesCollection.getName());
    }
}
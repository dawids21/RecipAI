package xyz.stasiak.recipai.recipes.collections;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.PermissionsFacade;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.recipes.collections.dto.*;
import xyz.stasiak.recipai.recipes.collections.exception.RecipesCollectionNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipesCollectionService {

    static final String RECIPES_COLLECTION_RESOURCE = "RECIPES_COLLECTION";

    private final RecipesCollectionRepository recipesCollectionRepository;
    private final PermissionsFacade permissionsFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final LimitsFacade limitsFacade;

    LimitBalance balance(String userEmail) {
        log.debug("Getting recipes collection balance for user: {}", userEmail);
        return limitsFacade.getBalance(userEmail, RECIPES_COLLECTION_RESOURCE).orElse(LimitBalance.zero());
    }

    List<RecipesCollectionListDto> findAll(String userEmail) {
        log.debug("Fetching all recipes collections for user: {}", userEmail);
        Set<UUID> ids = permissionsFacade.accessibleResources(RECIPES_COLLECTION_RESOURCE, userEmail).keySet();
        if (ids.isEmpty()) {
            return List.of();
        }
        return recipesCollectionRepository.findByIdInOrderByCreatedAtAsc(ids).stream()
                .map(this::toListDto)
                .toList();
    }

    public RecipesCollectionListDto findById(UUID collectionId, String userEmail) {
        log.debug("Finding collection for id: {} by user: {}", collectionId, userEmail);

        RecipesCollection collection = recipesCollectionRepository.findById(collectionId)
                .orElseThrow(() -> new RecipesCollectionNotFoundException(collectionId));

        permissionsFacade.requireEditor(RECIPES_COLLECTION_RESOURCE, collectionId, userEmail);

        return toListDto(collection);
    }

    @Transactional
    RecipesCollectionListDto create(CreateRecipesCollectionRequest request, String userEmail) {
        limitsFacade.reserve(userEmail, RECIPES_COLLECTION_RESOURCE);

        log.debug("Creating recipes collection with name: {} for user: {}", request.name(), userEmail);

        RecipesCollection recipesCollection = new RecipesCollection(request.name());
        RecipesCollection savedCollection = recipesCollectionRepository.save(recipesCollection);

        permissionsFacade.grantOwner(RECIPES_COLLECTION_RESOURCE, savedCollection.getId(), userEmail);

        log.debug("Recipes collection created with id: {} for user: {}", savedCollection.getId(), userEmail);
        return toListDto(savedCollection);
    }

    RecipesCollectionListDto updateById(UUID id, UpdateRecipesCollectionRequest request, String userEmail) {
        log.debug("Updating recipes collection with id: {} for user: {}", id, userEmail);

        RecipesCollection recipesCollection = recipesCollectionRepository.findById(id)
                .orElseThrow(() -> new RecipesCollectionNotFoundException(id));

        permissionsFacade.requireEditor(RECIPES_COLLECTION_RESOURCE, id, userEmail);

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

        permissionsFacade.requireOwner(RECIPES_COLLECTION_RESOURCE, id, userEmail);

        log.debug("Clearing permissions and pending invites for recipes collection {}", id);
        permissionsFacade.resourceDeleted(RECIPES_COLLECTION_RESOURCE, id);

        recipesCollectionRepository.deleteById(id);

        limitsFacade.release(userEmail, RECIPES_COLLECTION_RESOURCE);
    }

    void shareRecipesCollection(ShareRequest request, UUID collectionId, String requesterEmail) {
        log.debug("Sharing recipes collection {} from {} to {}", collectionId, requesterEmail, request.email());

        RecipesCollection collection = recipesCollectionRepository.findById(collectionId)
                .orElseThrow(() -> new RecipesCollectionNotFoundException(collectionId));

        permissionsFacade.requireEditor(RECIPES_COLLECTION_RESOURCE, collectionId, requesterEmail);

        permissionsFacade.invite(RECIPES_COLLECTION_RESOURCE, collectionId, request.email(), request.role(),
                collection.getName(), requesterEmail);

        log.info("Recipes collection {} invite created from {} to {}", collectionId, requesterEmail, request.email());
    }

    @Transactional
    void unshareRecipesCollection(String targetEmail, UUID collectionId, String requesterEmail) {
        log.debug("Unsharing recipes collection {} from {} for {}", collectionId, requesterEmail, targetEmail);

        if (!recipesCollectionRepository.existsById(collectionId)) {
            throw new RecipesCollectionNotFoundException(collectionId);
        }

        permissionsFacade.requireEditor(RECIPES_COLLECTION_RESOURCE, collectionId, requesterEmail);

        // Read before the write: revoke() cannot tell us afterwards whether it removed a permission
        // or cancelled an invite, and only the first should detach recipes.
        boolean hadPermission = permissionsFacade.roleOf(RECIPES_COLLECTION_RESOURCE, collectionId, targetEmail).isPresent();

        permissionsFacade.revoke(RECIPES_COLLECTION_RESOURCE, collectionId, targetEmail, requesterEmail);

        if (hadPermission) {
            eventPublisher.publishEvent(new RecipesCollectionUnshared(collectionId, targetEmail));
        }

        log.info("Recipes collection {} unshared from {} for {}", collectionId, requesterEmail, targetEmail);
    }

    List<PermissionDto> getPermissions(UUID collectionId, String userEmail) {
        log.debug("Getting permissions for recipes collection: {} by user: {}", collectionId, userEmail);

        if (!recipesCollectionRepository.existsById(collectionId)) {
            throw new RecipesCollectionNotFoundException(collectionId);
        }

        permissionsFacade.requireEditor(RECIPES_COLLECTION_RESOURCE, collectionId, userEmail);

        return permissionsFacade.getPermissions(RECIPES_COLLECTION_RESOURCE, collectionId);
    }

    public Optional<ResourceRole> roleOf(UUID collectionId, String userEmail) {
        log.debug("Getting role on collection: {} for user: {}", collectionId, userEmail);
        return permissionsFacade.roleOf(RECIPES_COLLECTION_RESOURCE, collectionId, userEmail);
    }

    public Set<UUID> accessibleCollectionIds(String userEmail) {
        log.debug("Getting accessible collection ids for user: {}", userEmail);
        return permissionsFacade.accessibleResources(RECIPES_COLLECTION_RESOURCE, userEmail).keySet();
    }

    private RecipesCollectionListDto toListDto(RecipesCollection recipesCollection) {
        return new RecipesCollectionListDto(recipesCollection.getId(), recipesCollection.getName());
    }
}

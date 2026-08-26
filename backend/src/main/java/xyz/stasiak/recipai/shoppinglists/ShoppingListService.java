package xyz.stasiak.recipai.shoppinglists;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.limits.LimitQuota;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.PermissionsFacade;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.shoppinglists.dto.*;
import xyz.stasiak.recipai.shoppinglists.exception.ItemNotFoundException;
import xyz.stasiak.recipai.shoppinglists.exception.ItemVersionConflictException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ShoppingListService {

    static final String SHOPPING_LIST_RESOURCE = "SHOPPING_LIST";
    static final String SHOPPING_LIST_ITEM_RESOURCE = "SHOPPING_LIST_ITEM";

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final PermissionsFacade permissionsFacade;
    private final LimitsFacade limitsFacade;

    LimitBalance balance(String userEmail) {
        log.debug("Getting shopping list balance for user: {}", userEmail);
        return limitsFacade.getBalance(userEmail, SHOPPING_LIST_RESOURCE).orElse(LimitBalance.zero());
    }

    @Transactional
    Optional<LimitQuota> itemQuota(UUID listId, String userEmail) {
        log.debug("Getting item quota for shopping list: {} requested by user: {}", listId, userEmail);
        requireEditorPermission(listId, userEmail);
        return limitsFacade.getQuota(requireOwnerEmail(listId), SHOPPING_LIST_ITEM_RESOURCE);
    }

    List<ShoppingListListDto> findAll(String userEmail) {
        log.debug("Fetching all shopping lists for user: {}", userEmail);
        Map<UUID, ResourceRole> access = permissionsFacade.accessibleResources(SHOPPING_LIST_RESOURCE, userEmail);
        if (access.isEmpty()) {
            return List.of();
        }
        return shoppingListRepository.findByIdInOrderByCreatedAtAsc(access.keySet()).stream()
                .map(this::toListDto)
                .toList();
    }

    @Transactional
    ShoppingListListDto create(CreateShoppingListRequest request, String userEmail) {
        limitsFacade.reserve(userEmail, SHOPPING_LIST_RESOURCE);

        log.debug("Creating shopping list with name: {} for user: {}", request.name(), userEmail);

        ShoppingList shoppingList = new ShoppingList(request.name());
        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        permissionsFacade.grantOwner(SHOPPING_LIST_RESOURCE, savedList.getId(), userEmail);

        log.debug("Shopping list created with id: {} for user: {}", savedList.getId(), userEmail);
        return toListDto(savedList);
    }

    ShoppingListDto findById(UUID id, String userEmail) {
        log.debug("Fetching shopping list with id: {} for user: {}", id, userEmail);

        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        ResourceRole role = requireEditorPermission(id, userEmail);

        List<ShoppingListItem> items = shoppingListItemRepository.findByShoppingListIdOrderByPositionAscIdAsc(shoppingList.getId());

        return toDto(shoppingList, items, role);
    }

    @Transactional
    ShoppingListItemDto createItem(UUID listId, CreateShoppingListItemRequest request, String userEmail) {
        log.debug("Creating item in shopping list: {} for user: {}", listId, userEmail);

        requireEditorPermission(listId, userEmail);

        String ownerEmail = requireOwnerEmail(listId);
        limitsFacade.reserve(ownerEmail, listId.toString(), SHOPPING_LIST_ITEM_RESOURCE);

        ShoppingListItem item = new ShoppingListItem(listId, request.name(), request.quantity(), request.unit(), request.checked(), request.position());
        ShoppingListItem savedItem = shoppingListItemRepository.save(item);

        return toItemDto(savedItem);
    }

    @Transactional
    ShoppingListItemDto updateItem(UUID listId, UUID itemId, UpdateShoppingListItemRequest request, String userEmail) {
        log.debug("Updating item: {} in shopping list: {} for user: {}", itemId, listId, userEmail);

        requireEditorPermission(listId, userEmail);

        ShoppingListItem item = shoppingListItemRepository.findByIdAndShoppingListId(itemId, listId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        if (!item.getVersion().equals(request.baseVersion())) {
            throw new ItemVersionConflictException(toItemDto(item));
        }

        item.setName(request.name());
        item.setQuantity(request.quantity());
        item.setUnit(request.unit());
        item.setChecked(request.checked());
        item.setPosition(request.position());

        try {
            ShoppingListItem savedItem = shoppingListItemRepository.saveAndFlush(item);
            return toItemDto(savedItem);
        } catch (ObjectOptimisticLockingFailureException ex) {
            ShoppingListItem winner = shoppingListItemRepository.findByIdAndShoppingListId(itemId, listId)
                    .orElseThrow(() -> new ItemNotFoundException(itemId));
            throw new ItemVersionConflictException(toItemDto(winner));
        }
    }

    @Transactional
    void deleteItem(UUID listId, UUID itemId, long baseVersion, String userEmail) {
        log.debug("Deleting item: {} in shopping list: {} for user: {}", itemId, listId, userEmail);

        requireEditorPermission(listId, userEmail);

        ShoppingListItem item = shoppingListItemRepository.findByIdAndShoppingListId(itemId, listId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        if (!item.getVersion().equals(baseVersion)) {
            throw new ItemVersionConflictException(toItemDto(item));
        }

        try {
            shoppingListItemRepository.delete(item);
            shoppingListItemRepository.flush();
        } catch (ObjectOptimisticLockingFailureException ex) {
            ShoppingListItem winner = shoppingListItemRepository.findByIdAndShoppingListId(itemId, listId)
                    .orElseThrow(() -> new ItemNotFoundException(itemId));
            throw new ItemVersionConflictException(toItemDto(winner));
        }

        limitsFacade.release(requireOwnerEmail(listId), listId.toString(), SHOPPING_LIST_ITEM_RESOURCE);
    }

    private ResourceRole requireEditorPermission(UUID listId, String userEmail) {
        if (!shoppingListRepository.existsById(listId)) {
            throw new ShoppingListNotFoundException(listId);
        }
        return permissionsFacade.requireEditor(SHOPPING_LIST_RESOURCE, listId, userEmail);
    }

    private String requireOwnerEmail(UUID listId) {
        return permissionsFacade.ownerEmail(SHOPPING_LIST_RESOURCE, listId)
                .orElseThrow(() -> new ShoppingListNotFoundException(listId));
    }

    @Transactional
    void deleteById(UUID id, String userEmail) {
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);

        if (!shoppingListRepository.existsById(id)) {
            throw new ShoppingListNotFoundException(id);
        }

        permissionsFacade.requireOwner(SHOPPING_LIST_RESOURCE, id, userEmail);

        log.debug("Clearing permissions and pending invites for shopping list {}", id);
        permissionsFacade.resourceDeleted(SHOPPING_LIST_RESOURCE, id);

        shoppingListRepository.deleteById(id);

        limitsFacade.release(userEmail, SHOPPING_LIST_RESOURCE);
        limitsFacade.clear(id.toString(), SHOPPING_LIST_ITEM_RESOURCE);
    }

    ShoppingListListDto updateById(UUID id, UpdateShoppingListRequest request, String userEmail) {
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);

        requireEditorPermission(id, userEmail);

        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        shoppingList.setName(request.name());

        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        return toListDto(savedList);
    }

    private ShoppingListListDto toListDto(ShoppingList list) {
        return new ShoppingListListDto(list.getId(), list.getName());
    }

    private ShoppingListDto toDto(ShoppingList list, List<ShoppingListItem> items, ResourceRole role) {
        List<ShoppingListItemDto> itemDtos = items.stream()
                .map(this::toItemDto)
                .toList();

        return new ShoppingListDto(list.getId(), list.getName(), itemDtos, role);
    }

    private ShoppingListItemDto toItemDto(ShoppingListItem item) {
        return new ShoppingListItemDto(
                item.getId(),
                item.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.getChecked(),
                item.getPosition(),
                item.getVersion()
        );
    }

    void shareShoppingList(ShareRequest request, UUID shoppingListId, String requesterEmail) {
        log.debug("Sharing shopping list {} from {} to {}", shoppingListId, requesterEmail, request.email());

        ShoppingList shoppingList = shoppingListRepository.findById(shoppingListId)
                .orElseThrow(() -> new ShoppingListNotFoundException(shoppingListId));

        permissionsFacade.requireEditor(SHOPPING_LIST_RESOURCE, shoppingListId, requesterEmail);

        permissionsFacade.invite(SHOPPING_LIST_RESOURCE, shoppingListId, request.email(), request.role(),
                shoppingList.getName(), requesterEmail);

        log.info("Shopping list {} invite created from {} to {}", shoppingListId, requesterEmail, request.email());
    }

    void unshareShoppingList(String targetEmail, UUID shoppingListId, String requesterEmail) {
        log.debug("Unsharing shopping list {} from {} for {}", shoppingListId, requesterEmail, targetEmail);

        if (!shoppingListRepository.existsById(shoppingListId)) {
            throw new ShoppingListNotFoundException(shoppingListId);
        }

        permissionsFacade.requireEditor(SHOPPING_LIST_RESOURCE, shoppingListId, requesterEmail);

        permissionsFacade.revoke(SHOPPING_LIST_RESOURCE, shoppingListId, targetEmail, requesterEmail);

        log.info("Shopping list {} unshared from {} for {}", shoppingListId, requesterEmail, targetEmail);
    }

    List<PermissionDto> getPermissions(UUID shoppingListId, String userEmail) {
        log.debug("Getting permissions for shopping list: {} by user: {}", shoppingListId, userEmail);

        if (!shoppingListRepository.existsById(shoppingListId)) {
            throw new ShoppingListNotFoundException(shoppingListId);
        }

        permissionsFacade.requireEditor(SHOPPING_LIST_RESOURCE, shoppingListId, userEmail);

        return permissionsFacade.getPermissions(SHOPPING_LIST_RESOURCE, shoppingListId);
    }

}

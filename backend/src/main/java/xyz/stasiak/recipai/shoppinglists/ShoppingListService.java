package xyz.stasiak.recipai.shoppinglists;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.shoppinglists.dto.*;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListAccessDeniedException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final ShoppingListPermissionRepository permissionRepository;

    List<ShoppingListListDto> findAll(String userEmail) {
        log.debug("Fetching all shopping lists for user: {}", userEmail);
        return shoppingListRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toListDto)
                .toList();
    }

    @Transactional
    ShoppingListListDto create(CreateShoppingListRequest request, String userEmail) {
        log.debug("Creating shopping list with name: {} for user: {}", request.name(), userEmail);

        ShoppingList shoppingList = new ShoppingList(request.name());
        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        ShoppingListPermissionId permissionId = new ShoppingListPermissionId(userEmail, savedList.getId());
        ShoppingListPermission permission = new ShoppingListPermission(permissionId, UserRole.OWNER);
        permissionRepository.save(permission);

        log.debug("Shopping list created with id: {} for user: {}", savedList.getId(), userEmail);
        return toListDto(savedList);
    }

    ShoppingListDto findById(UUID id, String userEmail) {
        log.debug("Fetching shopping list with id: {} for user: {}", id, userEmail);

        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        ShoppingListPermission permission = permissionRepository.findById(new ShoppingListPermissionId(userEmail, id))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        if (!permission.hasEditorRights()) {
            throw new ShoppingListAccessDeniedException(id);
        }

        List<ShoppingListItem> items = shoppingListItemRepository.findByShoppingListIdOrderByPositionAsc(shoppingList.getId());

        return toDto(shoppingList, items, permission.getRole());
    }

    @Transactional
    void deleteById(UUID id, String userEmail) {
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);

        if (!shoppingListRepository.existsById(id)) {
            throw new ShoppingListNotFoundException(id);
        }

        ShoppingListPermission permission = permissionRepository.findById(new ShoppingListPermissionId(userEmail, id))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        if (!permission.hasOwnerRights()) {
            throw new ShoppingListAccessDeniedException(id);
        }

        log.debug("Deleting all permissions for shopping list {}", id);
        permissionRepository.deleteAllByShoppingListId(id);

        // Delete the shopping list itself
        shoppingListRepository.deleteById(id);
    }

    ShoppingListListDto updateById(UUID id, UpdateShoppingListRequest request, String userEmail) {
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);

        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        ShoppingListPermission permission = permissionRepository.findById(new ShoppingListPermissionId(userEmail, id))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        if (!permission.hasEditorRights()) {
            throw new ShoppingListAccessDeniedException(id);
        }

        shoppingList.setName(request.name());

        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        return toListDto(savedList);
    }

    private ShoppingListListDto toListDto(ShoppingList list) {
        return new ShoppingListListDto(list.getId(), list.getName());
    }

    private ShoppingListDto toDto(ShoppingList list, List<ShoppingListItem> items, UserRole role) {
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

    void shareShoppingList(String targetEmail, UUID shoppingListId, String requesterEmail) {
        log.debug("Sharing shopping list {} from {} to {}", shoppingListId, requesterEmail, targetEmail);

        // Validate shopping list exists
        if (!shoppingListRepository.existsById(shoppingListId)) {
            throw new ShoppingListNotFoundException(shoppingListId);
        }

        // Validate requester has access (OWNER or EDITOR can share)
        permissionRepository.findById(new ShoppingListPermissionId(requesterEmail, shoppingListId))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(shoppingListId));

        // Check if target already has access - no-op if already shared
        ShoppingListPermissionId targetPermissionId = new ShoppingListPermissionId(targetEmail, shoppingListId);
        if (permissionRepository.findById(targetPermissionId).isPresent()) {
            log.warn("Shopping list {} is already shared with user {}", shoppingListId, targetEmail);
            return;
        }

        // Create EDITOR permission for target user
        ShoppingListPermission permission = new ShoppingListPermission(targetPermissionId, UserRole.EDITOR);
        permissionRepository.save(permission);

        log.info("Shopping list {} shared from {} to {}", shoppingListId, requesterEmail, targetEmail);
    }

    void unshareShoppingList(String targetEmail, UUID shoppingListId, String requesterEmail) {
        log.debug("Unsharing shopping list {} from {} for {}", shoppingListId, requesterEmail, targetEmail);

        // Validate shopping list exists
        if (!shoppingListRepository.existsById(shoppingListId)) {
            throw new ShoppingListNotFoundException(shoppingListId);
        }

        // Validate requester has access
        permissionRepository.findById(new ShoppingListPermissionId(requesterEmail, shoppingListId))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(shoppingListId));

        // Get target user's permission
        ShoppingListPermissionId targetPermissionId = new ShoppingListPermissionId(targetEmail, shoppingListId);
        ShoppingListPermission targetPermission = permissionRepository.findById(targetPermissionId)
                .orElse(null);

        // Prevent unsharing OWNER
        if (targetPermission != null && targetPermission.hasOwnerRights()) {
            if (targetEmail.equals(requesterEmail)) {
                log.warn("OWNER {} cannot unshare themselves from shopping list {}", requesterEmail, shoppingListId);
            } else {
                log.warn("Cannot unshare OWNER {} from shopping list {}", targetEmail, shoppingListId);
            }
            throw new ShoppingListAccessDeniedException(shoppingListId);
        }

        // Remove EDITOR permission (deleteById is no-op if record doesn't exist)
        permissionRepository.deleteById(targetPermissionId);

        log.info("Shopping list {} unshared from {} for {}", shoppingListId, requesterEmail, targetEmail);
    }

    List<SharedUserDto> getSharedUsers(UUID shoppingListId, String userEmail) {
        log.debug("Getting shared users for shopping list: {} by user: {}", shoppingListId, userEmail);

        // Validate shopping list exists
        if (!shoppingListRepository.existsById(shoppingListId)) {
            throw new ShoppingListNotFoundException(shoppingListId);
        }

        // Validate user has access
        permissionRepository.findById(new ShoppingListPermissionId(userEmail, shoppingListId))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(shoppingListId));

        // Return all users with access, OWNER first
        return permissionRepository.findAllByShoppingListId(shoppingListId).stream()
                .map(perm -> new SharedUserDto(perm.getId().email(), perm.getRole()))
                .toList();
    }

}

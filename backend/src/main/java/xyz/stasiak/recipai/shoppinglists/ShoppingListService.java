package xyz.stasiak.recipai.shoppinglists;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemCheckboxRepository shoppingListItemCheckboxRepository;
    private final ShoppingListPermissionRepository shoppingListPermissionRepository;

    List<ShoppingListListDto> findAll(String userEmail) {
        log.debug("Fetching all shopping lists for user: {}", userEmail);
        return shoppingListRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toListDto)
                .toList();
    }

    ShoppingListListDto create(CreateShoppingListRequest request, String userEmail) {
        log.debug("Creating shopping list with name: {} for user: {}", request.name(), userEmail);

        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setName(request.name());
        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        // Create ShoppingListPermission association with OWNER role
        ShoppingListPermission permission = new ShoppingListPermission();
        ShoppingListPermissionId permissionId = new ShoppingListPermissionId(userEmail, savedList.getId());
        permission.setId(permissionId);
        permission.setRole(UserRole.OWNER);
        shoppingListPermissionRepository.save(permission);

        log.debug("Shopping list created with id: {} for user: {}", savedList.getId(), userEmail);
        return toListDto(savedList);
    }

    private ShoppingListListDto toListDto(ShoppingList list) {
        return new ShoppingListListDto(list.getId(), list.getName(), list.getVersion());
    }

    ShoppingListDto findById(UUID id, String userEmail) {
        log.debug("Fetching shopping list with id: {} for user: {}", id, userEmail);

        List<ShoppingListView> rows = shoppingListRepository.findShoppingListViewWithItemsById(id);

        // Check if shopping list exists (404 if not found)
        if (rows.isEmpty()) {
            throw new ShoppingListNotFoundException(id);
        }

        // Check user permission (403 if no permission)
        UserRole role = shoppingListPermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        ShoppingListView firstRow = rows.getFirst();
        UUID listId = firstRow.getListId();
        String listName = firstRow.getListName();
        Long listVersion = firstRow.getListVersion();

        // Map items from rows (skip rows with null item_id - empty list case)
        List<ShoppingListItemDto> itemDtos = rows.stream()
                .filter(row -> row.getItemId() != null)
                .map(row -> new ShoppingListItemDto(
                        row.getItemId(),
                        row.getItemName(),
                        row.getItemQuantity(),
                        row.getItemUnit(),
                        row.getItemChecked(),
                        row.getItemPosition()
                ))
                .toList();

        return new ShoppingListDto(listId, listName, listVersion, itemDtos, role);
    }

    @Transactional
    void deleteById(UUID id, String userEmail, Long expectedVersion) {
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);

        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        // Check user permission (403 if no permission)
        UserRole userRole = shoppingListPermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        // Only OWNER can delete (403 if not OWNER)
        if (userRole != UserRole.OWNER) {
            throw new ShoppingListAccessDeniedException(id);
        }

        // Version check (optimistic lock)
        if (!shoppingList.getVersion().equals(expectedVersion)) {
            throw new ShoppingListPreconditionFailedException(id);
        }

        // Delete permissions first (items cascade automatically via DB constraint)
        shoppingListPermissionRepository.deleteAllByShoppingListId(id);

        // Delete the shopping list itself
        shoppingListRepository.deleteById(id);
    }

    @Transactional
    ShoppingListListDto updateById(UUID id, UpdateShoppingListRequest request, String userEmail, Long expectedVersion) {
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);

        // Fetch entity for optimistic locking
        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        // Check user permission (403 if no permission)
        UserRole userRole = shoppingListPermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        // Both OWNER and EDITOR can update (403 if neither)
        if (userRole != UserRole.OWNER && userRole != UserRole.EDITOR) {
            throw new ShoppingListAccessDeniedException(id);
        }

        // Version check (optimistic lock)
        if (!shoppingList.getVersion().equals(expectedVersion)) {
            throw new ShoppingListPreconditionFailedException(id);
        }

        // Update name
        shoppingList.setName(request.name());

        // Save and flush to detect concurrent modifications
        try {
            ShoppingList savedList = shoppingListRepository.saveAndFlush(shoppingList);
            return new ShoppingListListDto(savedList.getId(), savedList.getName(), savedList.getVersion());
        } catch (OptimisticLockingFailureException e) {
            throw new ShoppingListPreconditionFailedException(id, e);
        }
    }
}

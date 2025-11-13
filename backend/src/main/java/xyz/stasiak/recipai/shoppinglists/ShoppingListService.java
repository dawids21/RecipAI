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

    ShoppingListDto findById(UUID id, String userEmail) {
        log.debug("Fetching shopping list with id: {} for user: {}", id, userEmail);

        // Check if shopping list exists first (404 if not found)
        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        // Check user permission (403 if no permission)
        UserRole role = shoppingListPermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        // Fetch items and build DTO
        List<ShoppingListItem> items = shoppingListItemRepository
                .findByListIdOrderByPositionAsc(shoppingList.getId());

        return toDto(shoppingList, items, role);
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

    @Transactional
    void deleteById(UUID id, String userEmail) {
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);

        // Check if shopping list exists first (404 if not found)
        if (!shoppingListRepository.existsById(id)) {
            throw new ShoppingListNotFoundException(id);
        }

        // Check user permission (403 if no permission)
        UserRole userRole = shoppingListPermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        // Only OWNER can delete (403 if not OWNER)
        if (userRole != UserRole.OWNER) {
            throw new ShoppingListAccessDeniedException(id);
        }

        // Delete permissions first (items cascade automatically via DB constraint)
        shoppingListPermissionRepository.deleteAllByShoppingListId(id);

        // Delete the shopping list itself
        shoppingListRepository.deleteById(id);
    }

    ShoppingListListDto updateById(UUID id, UpdateShoppingListRequest request, String userEmail) {
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);

        // Check if shopping list exists first (404 if not found)
        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        // Check user permission (403 if no permission)
        UserRole userRole = shoppingListPermissionRepository.getUserRole(userEmail, id)
                .orElseThrow(() -> new ShoppingListAccessDeniedException(id));

        // Both OWNER and EDITOR can update (403 if neither)
        if (userRole != UserRole.OWNER && userRole != UserRole.EDITOR) {
            throw new ShoppingListAccessDeniedException(id);
        }

        // Update name
        shoppingList.setName(request.name());

        // Save and return simple DTO (id and name only)
        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        return new ShoppingListListDto(savedList.getId(), savedList.getName());
    }
}

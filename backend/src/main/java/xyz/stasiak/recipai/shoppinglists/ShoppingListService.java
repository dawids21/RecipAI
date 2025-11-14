package xyz.stasiak.recipai.shoppinglists;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.shoppinglists.dto.CreateShoppingListRequest;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListDto;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListListDto;
import xyz.stasiak.recipai.shoppinglists.dto.UpdateShoppingListRequest;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListAccessDeniedException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListNotFoundException;
import xyz.stasiak.recipai.shoppinglists.items.ShoppingListItemService;
import xyz.stasiak.recipai.shoppinglists.items.dto.ShoppingListItemDto;
import xyz.stasiak.recipai.shoppinglists.permissions.ShoppingListPermissionService;
import xyz.stasiak.recipai.shoppinglists.permissions.UserRole;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemService shoppingListItemService;
    private final ShoppingListPermissionService permissionService;

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

        // Create OWNER permission for the creator
        permissionService.addOwnerPermission(userEmail, savedList.getId());

        log.debug("Shopping list created with id: {} for user: {}", savedList.getId(), userEmail);
        return toListDto(savedList);
    }

    ShoppingListDto findById(UUID id, String userEmail) {
        log.debug("Fetching shopping list with id: {} for user: {}", id, userEmail);

        // Check if shopping list exists first (404 if not found)
        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        // Check user permission (403 if no permission or insufficient role)
        if (!permissionService.checkEditorOrOwnerPermission(userEmail, id)) {
            throw new ShoppingListAccessDeniedException(id);
        }
        UserRole role = permissionService.getUserRole(userEmail, id).orElseThrow();

        // Fetch items using ShoppingListItemService
        List<ShoppingListItemDto> items = shoppingListItemService.findItemsForShoppingList(id);

        return new ShoppingListDto(shoppingList.getId(), shoppingList.getName(), items, role);
    }

    private ShoppingListListDto toListDto(ShoppingList list) {
        return new ShoppingListListDto(list.getId(), list.getName());
    }

    @Transactional
    void deleteById(UUID id, String userEmail) {
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);

        // Check if shopping list exists first (404 if not found)
        if (!shoppingListRepository.existsById(id)) {
            throw new ShoppingListNotFoundException(id);
        }

        // Check OWNER permission (403 if not OWNER or no permission)
        if (!permissionService.checkOwnerPermission(userEmail, id)) {
            throw new ShoppingListAccessDeniedException(id);
        }

        // Delete permissions first (items cascade automatically via DB constraint)
        permissionService.deleteAllPermissions(id);

        // Delete the shopping list itself
        shoppingListRepository.deleteById(id);
    }

    ShoppingListListDto updateById(UUID id, UpdateShoppingListRequest request, String userEmail) {
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);

        // Check if shopping list exists first (404 if not found)
        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        // Check OWNER or EDITOR permission (403 if neither or no permission)
        if (!permissionService.checkEditorOrOwnerPermission(userEmail, id)) {
            throw new ShoppingListAccessDeniedException(id);
        }

        // Update name
        shoppingList.setName(request.name());

        // Save and return simple DTO (id and name only)
        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        return new ShoppingListListDto(savedList.getId(), savedList.getName());
    }
}

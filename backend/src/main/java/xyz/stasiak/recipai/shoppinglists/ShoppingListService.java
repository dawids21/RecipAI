package xyz.stasiak.recipai.shoppinglists;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.shoppinglists.dto.*;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListAccessDeniedException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListItemNotFoundException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListItemVersionMismatchException;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListNotFoundException;

import java.math.BigDecimal;
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

    @Transactional
    ShoppingListItemDto createItem(UUID shoppingListId, CreateShoppingListItemRequest request, String userEmail) {
        log.debug("Creating item for shopping list {} by user {}", shoppingListId, userEmail);

        shoppingListRepository.findById(shoppingListId)
                .orElseThrow(() -> new ShoppingListNotFoundException(shoppingListId));

        ShoppingListPermission permission = permissionRepository.findById(new ShoppingListPermissionId(userEmail, shoppingListId))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(shoppingListId));

        if (!permission.hasEditorRights()) {
            throw new ShoppingListAccessDeniedException(shoppingListId);
        }

        BigDecimal position = calculateNextPosition(shoppingListId);

        ShoppingListItem item = new ShoppingListItem(
                shoppingListId,
                request.name(),
                request.quantity(),
                request.unit(),
                position
        );

        ShoppingListItem saved = shoppingListItemRepository.save(item);

        return toItemDto(saved);
    }

    @Transactional
    void deleteItem(UUID shoppingListId, UUID itemId, Long expectedVersion, String userEmail) {
        log.debug("Deleting item {} from shopping list {} by user {} with expected version {}",
                itemId, shoppingListId, userEmail, expectedVersion);

        ShoppingListItem item = shoppingListItemRepository.findById(itemId)
                .orElseThrow(() -> new ShoppingListItemNotFoundException(itemId));

        if (!item.getShoppingListId().equals(shoppingListId)) {
            throw new ShoppingListItemNotFoundException(itemId);
        }

        ShoppingListPermission permission = permissionRepository.findById(new ShoppingListPermissionId(userEmail, shoppingListId))
                .orElseThrow(() -> new ShoppingListAccessDeniedException(shoppingListId));

        if (!permission.hasEditorRights()) {
            throw new ShoppingListAccessDeniedException(shoppingListId);
        }

        if (!item.getVersion().equals(expectedVersion)) {
            throw new ShoppingListItemVersionMismatchException(itemId, expectedVersion, item.getVersion());
        }

        shoppingListItemRepository.deleteById(itemId);
    }

    private BigDecimal calculateNextPosition(UUID shoppingListId) {
        log.debug("Calculating next position for shopping list: {}", shoppingListId);

        var maxPosition = shoppingListItemRepository.findMaxPositionByShoppingListId(shoppingListId);

        if (maxPosition.isEmpty()) {
            // No items yet, start at 1.0
            log.debug("No items found, using position 1.0");
            return BigDecimal.ONE;
        }

        // Add 1.0 to max position
        BigDecimal nextPosition = maxPosition.get().add(BigDecimal.ONE);
        log.debug("Max position: {}, next position: {}", maxPosition.get(), nextPosition);
        return nextPosition;
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

}

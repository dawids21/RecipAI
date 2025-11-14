package xyz.stasiak.recipai.shoppinglists.items;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.shoppinglists.exception.ShoppingListAccessDeniedException;
import xyz.stasiak.recipai.shoppinglists.items.dto.CreateShoppingListItemRequest;
import xyz.stasiak.recipai.shoppinglists.items.dto.ShoppingListItemDto;
import xyz.stasiak.recipai.shoppinglists.items.exception.ShoppingListItemNotFoundException;
import xyz.stasiak.recipai.shoppinglists.permissions.ShoppingListPermissionService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShoppingListItemService {

    private final ShoppingListItemRepository repository;
    private final ShoppingListPermissionService permissionService;

    @Transactional
    public ShoppingListItemDto create(UUID shoppingListId, CreateShoppingListItemRequest request, String userEmail) {
        log.debug("Creating item for shopping list {} by user {}", shoppingListId, userEmail);

        // Step 1: Check user has EDITOR or OWNER permission (403 if not)
        if (!permissionService.checkEditorOrOwnerPermission(userEmail, shoppingListId)) {
            throw new ShoppingListAccessDeniedException(shoppingListId);
        }

        // Step 2: Calculate next position (always add to end)
        BigDecimal position = calculateNextPosition(shoppingListId);

        // Step 3: Create new item entity
        ShoppingListItem item = new ShoppingListItem();
        item.setShoppingListId(shoppingListId);
        item.setName(request.name());
        item.setQuantity(request.quantity());
        item.setUnit(request.unit());
        item.setPosition(position);
        item.setChecked(false);  // Default value

        // Step 4: Save to database
        ShoppingListItem saved = repository.save(item);

        // Step 5: Convert to DTO and return
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID shoppingListId, UUID itemId, String userEmail) {
        log.debug("Deleting item {} from shopping list {} by user {}", itemId, shoppingListId, userEmail);

        // Step 1: Check if item exists (404 if not)
        ShoppingListItem item = repository.findById(itemId)
                .orElseThrow(() -> new ShoppingListItemNotFoundException(itemId));

        // Step 2: Verify item belongs to specified shopping list (404 if mismatch)
        if (!item.getShoppingListId().equals(shoppingListId)) {
            throw new ShoppingListItemNotFoundException(itemId);
        }

        // Step 3: Check user has EDITOR or OWNER permission (403 if not)
        if (!permissionService.checkEditorOrOwnerPermission(userEmail, shoppingListId)) {
            throw new ShoppingListAccessDeniedException(shoppingListId);
        }

        // Step 4: Delete the item
        repository.deleteById(itemId);
    }

    private BigDecimal calculateNextPosition(UUID shoppingListId) {
        log.debug("Calculating next position for shopping list: {}", shoppingListId);

        var maxPosition = repository.findMaxPositionByShoppingListId(shoppingListId);

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

    public List<ShoppingListItemDto> findItemsForShoppingList(UUID shoppingListId) {
        log.debug("Finding all items for shopping list: {}", shoppingListId);
        return repository.findByShoppingListIdOrderByPositionAsc(shoppingListId).stream()
                .map(this::toDto)
                .toList();
    }

    private ShoppingListItemDto toDto(ShoppingListItem item) {
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
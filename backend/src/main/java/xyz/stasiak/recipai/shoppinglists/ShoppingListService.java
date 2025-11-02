package xyz.stasiak.recipai.shoppinglists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;

    List<ShoppingListListDto> findAll() {
        log.debug("Fetching all shopping lists");
        return shoppingListRepository.findAll().stream()
                .map(this::toListDto)
                .toList();
    }

    ShoppingListListDto create(CreateShoppingListRequest request) {
        log.debug("Creating shopping list with name: {}", request.name());

        ShoppingList shoppingList = new ShoppingList();
        shoppingList.setName(request.name());
        ShoppingList savedList = shoppingListRepository.save(shoppingList);

        log.debug("Shopping list created with id: {}", savedList.getId());
        return toListDto(savedList);
    }

    ShoppingListDto findById(UUID id) {
        log.debug("Fetching shopping list with id: {}", id);

        ShoppingList shoppingList = shoppingListRepository.findById(id)
                .orElseThrow(() -> new ShoppingListNotFoundException(id));

        List<ShoppingListItem> items = shoppingListItemRepository
                .findByListIdOrderByPositionAsc(shoppingList.getId());

        return toDto(shoppingList, items);
    }

    private ShoppingListListDto toListDto(ShoppingList list) {
        return new ShoppingListListDto(list.getId(), list.getName());
    }

    private ShoppingListDto toDto(ShoppingList list, List<ShoppingListItem> items) {
        List<ShoppingListItemDto> itemDtos = items.stream()
                .map(this::toItemDto)
                .toList();

        return new ShoppingListDto(list.getId(), list.getName(), itemDtos);
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

package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shopping-lists")
@RequiredArgsConstructor
@Slf4j
class ShoppingListController {

    private final ShoppingListService shoppingListService;

    @GetMapping
    List<ShoppingListListDto> getAllShoppingLists() {
        log.debug("Getting all shopping lists");
        return shoppingListService.findAll();
    }

    @GetMapping("/{id}")
    ShoppingListDto getShoppingListById(@PathVariable UUID id) {
        log.debug("Getting shopping list by id: {}", id);
        return shoppingListService.findById(id);
    }

    @PostMapping
    ResponseEntity<ShoppingListListDto> createShoppingList(@Valid @RequestBody CreateShoppingListRequest request) {
        log.debug("Creating shopping list");
        ShoppingListListDto dto = shoppingListService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}

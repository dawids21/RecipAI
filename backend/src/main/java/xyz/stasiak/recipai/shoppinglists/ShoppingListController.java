package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xyz.stasiak.recipai.shoppinglists.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shopping-lists")
@RequiredArgsConstructor
@Slf4j
class ShoppingListController {

    private final ShoppingListService shoppingListService;

    @GetMapping
    List<ShoppingListListDto> getAllShoppingLists(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting shopping lists for user: {}", userEmail);
        return shoppingListService.findAll(userEmail);
    }

    @GetMapping("/{id}")
    ShoppingListDto getShoppingListById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting shopping list by id: {} for user: {}", id, userEmail);
        return shoppingListService.findById(id, userEmail);
    }

    @PostMapping
    ResponseEntity<ShoppingListListDto> createShoppingList(@Valid @RequestBody CreateShoppingListRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating shopping list for user: {}", userEmail);
        ShoppingListListDto dto = shoppingListService.create(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteShoppingList(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);
        shoppingListService.deleteById(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    ResponseEntity<ShoppingListListDto> updateShoppingList(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShoppingListRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);
        ShoppingListListDto updatedList = shoppingListService.updateById(id, request, userEmail);
        return ResponseEntity.ok(updatedList);
    }

    @PostMapping("/{shopping_list_id}/item")
    ResponseEntity<ShoppingListItemDto> createItem(
            @PathVariable("shopping_list_id") UUID shoppingListId,
            @Valid @RequestBody CreateShoppingListItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating item for shopping list {} by user {}", shoppingListId, userEmail);

        ShoppingListItemDto dto = shoppingListService.createItem(shoppingListId, request, userEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{shopping_list_id}/item/{id}")
    ResponseEntity<Void> deleteItem(
            @PathVariable("shopping_list_id") UUID shoppingListId,
            @PathVariable UUID id,
            @RequestHeader("If-Match") Long version,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting item {} from shopping list {} by user {} with version {}", id, shoppingListId, userEmail, version);

        shoppingListService.deleteItem(shoppingListId, id, version, userEmail);

        return ResponseEntity.noContent().build();
    }
}

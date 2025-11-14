package xyz.stasiak.recipai.shoppinglists.items;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xyz.stasiak.recipai.shoppinglists.items.dto.CreateShoppingListItemRequest;
import xyz.stasiak.recipai.shoppinglists.items.dto.ShoppingListItemDto;

import java.util.UUID;

@RestController
@RequestMapping("/shopping-lists")
@RequiredArgsConstructor
@Slf4j
class ShoppingListItemController {

    private final ShoppingListItemService service;

    @PostMapping("/{shopping_list_id}/item")
    ResponseEntity<ShoppingListItemDto> createItem(
            @PathVariable("shopping_list_id") UUID shoppingListId,
            @Valid @RequestBody CreateShoppingListItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating item for shopping list {} by user {}", shoppingListId, userEmail);

        ShoppingListItemDto dto = service.create(shoppingListId, request, userEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{shopping_list_id}/item/{id}")
    ResponseEntity<Void> deleteItem(
            @PathVariable("shopping_list_id") UUID shoppingListId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting item {} from shopping list {} by user {}", id, shoppingListId, userEmail);

        service.delete(shoppingListId, id, userEmail);

        return ResponseEntity.noContent().build();
    }
}
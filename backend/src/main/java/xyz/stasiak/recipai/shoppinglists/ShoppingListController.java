package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    ResponseEntity<Void> deleteShoppingList(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("If-Match") String ifMatchHeader
    ) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting shopping list with id: {} for user: {}", id, userEmail);
        Long expectedVersion = extractVersionFromETag(ifMatchHeader);
        shoppingListService.deleteById(id, userEmail, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    ResponseEntity<ShoppingListListDto> updateShoppingList(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShoppingListRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("If-Match") String ifMatchHeader
    ) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating shopping list with id: {} for user: {}", id, userEmail);
        Long expectedVersion = extractVersionFromETag(ifMatchHeader);
        ShoppingListListDto updatedList = shoppingListService.updateById(id, request, userEmail, expectedVersion);
        return ResponseEntity.ok(updatedList);
    }

    private Long extractVersionFromETag(String etag) {
        String versionStr = etag.replaceAll("^\"|\"$", "");
        return Long.parseLong(versionStr);
    }
}

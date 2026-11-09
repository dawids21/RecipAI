package xyz.stasiak.recipai.shoppinglists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShoppingListsFacade {

    private final ShoppingListService shoppingListService;

    public void deleteAllByEmail(String email) {
        log.debug("Deleting all shopping lists owned by user {}", email);
        shoppingListService.deleteAllByEmail(email);
    }
}

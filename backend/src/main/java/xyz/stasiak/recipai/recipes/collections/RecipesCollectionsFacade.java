package xyz.stasiak.recipai.recipes.collections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipesCollectionsFacade {

    private final RecipesCollectionService recipesCollectionService;

    public void deleteAllByEmail(String email) {
        log.debug("Deleting all recipes collections owned by user {}", email);
        recipesCollectionService.deleteAllByEmail(email);
    }
}

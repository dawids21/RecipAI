package xyz.stasiak.recipai.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.PermissionsFacade;
import xyz.stasiak.recipai.planning.PlanningFacade;
import xyz.stasiak.recipai.recipes.RecipeFacade;
import xyz.stasiak.recipai.recipes.collections.RecipesCollectionsFacade;
import xyz.stasiak.recipai.shoppinglists.ShoppingListsFacade;

@Service
@RequiredArgsConstructor
@Slf4j
class AccountDeletionService {

    private final PlanningFacade planningFacade;
    private final ShoppingListsFacade shoppingListsFacade;
    private final RecipeFacade recipeFacade;
    private final RecipesCollectionsFacade recipesCollectionsFacade;
    private final LimitsFacade limitsFacade;
    private final PermissionsFacade permissionsFacade;

    // Deliberately not @Transactional: boundaries stay per module (ADR-0010), and a partial purge is
    // repaired by calling this again.
    void deleteAccount(String email) {
        // Derived deletes bind a null as IS NULL, so a missing email would purge rows that belong to nobody.
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Cannot delete an account without an email");
        }
        log.info("Deleting account {}", email);

        // Resource modules first: each asks permissions what the email owns. Every module holding
        // owned resources must be purged here before permissions drops the OWNER rows below, or its
        // resources are left orphaned with no owner.
        planningFacade.deleteAllByEmail(email);
        shoppingListsFacade.deleteAllByEmail(email);
        recipeFacade.deleteAllByEmail(email);
        recipesCollectionsFacade.deleteAllByEmail(email);

        limitsFacade.deleteAllBySubject(email);

        // Last: it is what told every module above what to delete.
        permissionsFacade.deleteAllByEmail(email);

        log.info("Deleted account {}", email);
    }
}

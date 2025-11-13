package xyz.stasiak.recipai.shoppinglists.permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShoppingListPermissionService {

    private final ShoppingListPermissionRepository repository;

    /**
     * Creates OWNER permission for a user on a shopping list.
     * Called when a new shopping list is created.
     */
    public void addOwnerPermission(String userEmail, UUID shoppingListId) {
        log.debug("Creating OWNER permission for user {} on shopping list {}", userEmail, shoppingListId);

        ShoppingListPermission permission = new ShoppingListPermission();
        ShoppingListPermissionId permissionId = new ShoppingListPermissionId(userEmail, shoppingListId);
        permission.setId(permissionId);
        permission.setRole(UserRole.OWNER);
        repository.save(permission);

        log.debug("OWNER permission created successfully");
    }

    /**
     * Retrieves the user's role for a shopping list.
     * Returns empty Optional if user has no permission.
     */
    public Optional<UserRole> getUserRole(String userEmail, UUID shoppingListId) {
        return repository.getUserRole(userEmail, shoppingListId);
    }

    /**
     * Checks if user has OWNER role for the shopping list.
     * Returns true if user is OWNER, false otherwise (including no permission).
     */
    public boolean checkOwnerPermission(String userEmail, UUID shoppingListId) {
        Optional<UserRole> roleOpt = repository.getUserRole(userEmail, shoppingListId);

        if (roleOpt.isEmpty()) {
            log.debug("User {} has no permission for shopping list {}", userEmail, shoppingListId);
            return false;
        }

        boolean isOwner = roleOpt.get() == UserRole.OWNER;
        if (!isOwner) {
            log.debug("User {} has role {} for shopping list {}, but OWNER required",
                    userEmail, roleOpt.get(), shoppingListId);
        }
        return isOwner;
    }

    /**
     * Checks if user has OWNER or EDITOR role for the shopping list.
     * Returns true if user is OWNER or EDITOR, false otherwise (including no permission).
     */
    public boolean checkEditorOrOwnerPermission(String userEmail, UUID shoppingListId) {
        Optional<UserRole> roleOpt = repository.getUserRole(userEmail, shoppingListId);

        if (roleOpt.isEmpty()) {
            log.debug("User {} has no permission for shopping list {}", userEmail, shoppingListId);
            return false;
        }

        UserRole role = roleOpt.get();
        boolean hasPermission = role == UserRole.OWNER || role == UserRole.EDITOR;
        if (!hasPermission) {
            log.debug("User {} has role {} for shopping list {}, but OWNER or EDITOR required",
                    userEmail, role, shoppingListId);
        }
        return hasPermission;
    }

    /**
     * Deletes all permissions for a shopping list.
     * Used during shopping list deletion (cascade delete).
     * Must be called within a @Transactional context.
     */
    public void deleteAllPermissions(UUID shoppingListId) {
        log.debug("Deleting all permissions for shopping list {}", shoppingListId);
        repository.deleteAllByShoppingListId(shoppingListId);
    }
}
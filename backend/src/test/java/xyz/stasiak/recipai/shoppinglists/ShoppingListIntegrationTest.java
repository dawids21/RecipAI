package xyz.stasiak.recipai.shoppinglists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.LimitsEnabled;
import xyz.stasiak.recipai.RecomputeMigration;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.TestIdentities;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.permissions.dto.UnshareRequest;
import xyz.stasiak.recipai.shoppinglists.dto.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@IntegrationTest
class ShoppingListIntegrationTest {

    @LocalServerPort
    private int port;

    private String owner;
    private String user1;
    private String user2;

    @BeforeEach
    void freshUsers() {
        owner = TestIdentities.freshToken();
        user1 = TestIdentities.freshToken();
        user2 = TestIdentities.freshToken();
    }

    private RestClient restClient() {
        return restClient(owner);
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
    }

    private ShoppingListListDto createShoppingList(RestClient client, String name) {
        CreateShoppingListRequest request = new CreateShoppingListRequest(name);
        return client
                .post()
                .uri("/shopping-lists")
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);
    }

    private List<ShoppingListListDto> getAllShoppingLists(RestClient client) {
        return client
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private ShoppingListDto getShoppingList(RestClient client, UUID id) {
        return client
                .get()
                .uri("/shopping-lists/" + id)
                .retrieve()
                .body(ShoppingListDto.class);
    }

    private ShoppingListListDto updateShoppingList(RestClient client, UUID id, String newName) {
        UpdateShoppingListRequest request = new UpdateShoppingListRequest(newName);
        return client
                .put()
                .uri("/shopping-lists/" + id)
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);
    }

    private void deleteShoppingList(RestClient client, UUID id) {
        client
                .delete()
                .uri("/shopping-lists/" + id)
                .retrieve()
                .toBodilessEntity();
    }

    private void shareShoppingList(RestClient client, UUID shoppingListId, String email) {
        ShareRequest request = new ShareRequest(email, ResourceRole.EDITOR);
        client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareShoppingList(RestClient client, UUID shoppingListId, String email) {
        UnshareRequest request = new UnshareRequest(email);
        client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<PermissionDto> getPermissions(RestClient client, UUID shoppingListId) {
        return client
                .get()
                .uri("/shopping-lists/" + shoppingListId + "/permissions")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private List<PendingInviteDto> getPendingInvites(RestClient client) {
        return client
                .get()
                .uri("/invites")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void acceptInvite(RestClient client, UUID inviteId) {
        client
                .post()
                .uri("/invites/" + inviteId + "/accept")
                .retrieve()
                .toBodilessEntity();
    }

    private UUID findPendingInviteId(RestClient client, String resourceType) {
        return getPendingInvites(client).stream()
                .filter(invite -> invite.resourceType().equals(resourceType))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private void acceptPendingShoppingListInvite(RestClient client) {
        acceptInvite(client, findPendingInviteId(client, "SHOPPING_LIST"));
    }

    private ShoppingListItemDto createItem(RestClient client, UUID listId, CreateShoppingListItemRequest request) {
        return client
                .post()
                .uri("/shopping-lists/" + listId + "/items")
                .body(request)
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private CreateShoppingListItemRequest itemRequest(String name, BigDecimal quantity, String unit, BigDecimal position) {
        return new CreateShoppingListItemRequest(name, quantity, unit, false, position);
    }

    private CreateShoppingListItemRequest itemRequest(String name, BigDecimal quantity, String unit, boolean checked, BigDecimal position) {
        return new CreateShoppingListItemRequest(name, quantity, unit, checked, position);
    }

    private ShoppingListItemDto updateItem(RestClient client, UUID listId, UUID itemId, UpdateShoppingListItemRequest request) {
        return client
                .put()
                .uri("/shopping-lists/" + listId + "/items/" + itemId)
                .body(request)
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private UpdateShoppingListItemRequest updateRequest(long baseVersion, String name, BigDecimal quantity, String unit, boolean checked, BigDecimal position) {
        return new UpdateShoppingListItemRequest(baseVersion, name, quantity, unit, checked, position);
    }

    private void deleteItem(RestClient client, UUID listId, UUID itemId, long baseVersion) {
        client
                .delete()
                .uri("/shopping-lists/" + listId + "/items/" + itemId + "?baseVersion=" + baseVersion)
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldCreateAndListShoppingLists() {
        RestClient client = restClient();

        // Create first shopping list
        ShoppingListListDto response1 = createShoppingList(client, "Groceries");

        assertThat(response1).isNotNull();
        assertThat(response1.id()).isNotNull();
        assertThat(response1.name()).isEqualTo("Groceries");

        // Create second shopping list
        ShoppingListListDto response2 = createShoppingList(client, "Hardware");

        assertThat(response2).isNotNull();
        assertThat(response2.id()).isNotNull();
        assertThat(response2.name()).isEqualTo("Hardware");

        // List all shopping lists
        List<ShoppingListListDto> listResponse = getAllShoppingLists(client);

        assertThat(listResponse).isNotEmpty();
        assertThat(listResponse).hasSizeGreaterThanOrEqualTo(2);
        assertThat(listResponse)
                .extracting(ShoppingListListDto::name)
                .contains("Groceries", "Hardware");
    }

    @Test
    void shouldValidateCreateShoppingListRequest() {
        RestClient client = restClient();

        try {
            createShoppingList(client, "");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldGetShoppingListById() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto createdList = createShoppingList(client, "Weekly Groceries");

        assertThat(createdList).isNotNull();
        assertThat(createdList.id()).isNotNull();

        // Get the shopping list by ID
        ShoppingListDto response = getShoppingList(client, createdList.id());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(createdList.id());
        assertThat(response.name()).isEqualTo("Weekly Groceries");
        assertThat(response.items()).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.role()).isEqualTo(ResourceRole.OWNER);
    }

    @Test
    void shouldReturn404WhenShoppingListNotFound() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            getShoppingList(client, nonExistentId);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Shopping list not found with id: " + nonExistentId);
            assertThat(responseBody).contains("Shopping List Not Found");
        }
    }

    @Test
    void shouldIsolateShoppingListsBetweenUsers() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates a shopping list
        ShoppingListListDto user1List = createShoppingList(user1Client, "User 1 List");

        assertThat(user1List).isNotNull();
        assertThat(user1List.name()).isEqualTo("User 1 List");

        // User 2 creates a shopping list
        ShoppingListListDto user2List = createShoppingList(user2Client, "User 2 List");

        assertThat(user2List).isNotNull();
        assertThat(user2List.name()).isEqualTo("User 2 List");

        // User 1 should only see their own list
        List<ShoppingListListDto> user1Lists = getAllShoppingLists(user1Client);

        assertThat(user1Lists)
                .extracting(ShoppingListListDto::id)
                .contains(user1List.id())
                .doesNotContain(user2List.id());

        // User 2 should only see their own list
        List<ShoppingListListDto> user2Lists = getAllShoppingLists(user2Client);

        assertThat(user2Lists)
                .extracting(ShoppingListListDto::id)
                .contains(user2List.id())
                .doesNotContain(user1List.id());
    }

    @Test
    void shouldPreventCrossUserAccess() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates a shopping list
        ShoppingListListDto user1List = createShoppingList(user1Client, "User 1 Private List");

        assertThat(user1List).isNotNull();

        // User 2 tries to access User 1's list - should get 403 Forbidden
        try {
            getShoppingList(user2Client, user1List.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied to SHOPPING_LIST with id: " + user1List.id());
            assertThat(responseBody).contains("Resource Access Denied");
        }
    }

    @Test
    void shouldUpdateAndDeleteShoppingList() {
        RestClient client = restClient();

        // CREATE shopping list
        ShoppingListListDto created = createShoppingList(client, "My Shopping List");

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("My Shopping List");

        // UPDATE shopping list
        ShoppingListListDto updated = updateShoppingList(client, created.id(), "Updated List Name");

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("Updated List Name");

        // DELETE shopping list
        deleteShoppingList(client, created.id());

        // VERIFY deleted (should return 404)
        try {
            getShoppingList(client, created.id());
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentShoppingList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            updateShoppingList(client, nonExistentId, "Updated Name");
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentShoppingList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            deleteShoppingList(client, nonExistentId);
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenUnauthorizedUserTriesToUpdate() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates list (becomes OWNER)
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");

        assertThat(list).isNotNull();

        // User 2 tries to update (no permission) - should get 403
        try {
            updateShoppingList(user2Client, list.id(), "Hacked Name");
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn403WhenUnauthorizedUserTriesToDelete() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates list (becomes OWNER)
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");

        assertThat(list).isNotNull();

        // User 2 tries to delete (no permission) - should get 403
        try {
            deleteShoppingList(user2Client, list.id());
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldShareAndUnshareShoppingLists() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates a shopping list
        ShoppingListListDto list = createShoppingList(user1Client, "Shared List");
        assertThat(list).isNotNull();

        // User 2 cannot access initially
        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 shares with User 2 - creates a pending invite, grants nothing yet
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));

        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 accepts the invite
        acceptPendingShoppingListInvite(user2Client);

        // User 2 can now access the list
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
        assertThat(sharedList.name()).isEqualTo("Shared List");
        assertThat(sharedList.role()).isEqualTo(ResourceRole.EDITOR);

        // Verify the permissions list - both granted, neither pending
        List<PermissionDto> permissions = getPermissions(user1Client, list.id());
        assertThat(permissions).containsExactly(
                new PermissionDto(TestIdentities.emailOf(user1), ResourceRole.OWNER, false),
                new PermissionDto(TestIdentities.emailOf(user2), ResourceRole.EDITOR, false)
        );

        // User 1 unshares from User 2
        unshareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));

        // User 2 can no longer access
        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldAllowEditorsToShareAndUnshare() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates and shares with User 2, who accepts
        ShoppingListListDto list = createShoppingList(user1Client, "Editor Share Test");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));
        acceptPendingShoppingListInvite(user2Client);

        // User 2 (EDITOR) can share with another user
        shareShoppingList(user2Client, list.id(), TestIdentities.emailOf(owner));

        // Verify three entries: two granted, one pending
        List<PermissionDto> permissions = getPermissions(user1Client, list.id());
        assertThat(permissions).hasSize(3);
        assertThat(permissions)
                .filteredOn(PermissionDto::pending)
                .extracting(PermissionDto::email)
                .containsExactly(TestIdentities.emailOf(owner));

        // User 2 (EDITOR) can cancel the pending invite
        unshareShoppingList(user2Client, list.id(), TestIdentities.emailOf(owner));

        // Verify only two entries remain
        permissions = getPermissions(user1Client, list.id());
        assertThat(permissions).hasSize(2);
    }

    @Test
    void shouldPreventUnsharingOwner() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Unshare Owner Test");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));

        // User 2 tries to unshare User 1 (OWNER) - should fail
        try {
            unshareShoppingList(user2Client, list.id(), TestIdentities.emailOf(user1));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        List<PermissionDto> permissions = getPermissions(user1Client, list.id());
        assertThat(permissions).hasSize(2);
        assertThat(permissions)
                .extracting(PermissionDto::email)
                .contains(TestIdentities.emailOf(user1));
    }

    @Test
    void shouldPreventEditorFromUnsharingThemselves() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates and shares with User 2, who accepts
        ShoppingListListDto list = createShoppingList(user1Client, "Self Unshare Test");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));
        acceptPendingShoppingListInvite(user2Client);

        // User 2 cannot unshare themselves - the self-unshare guard now applies to every role
        try {
            unshareShoppingList(user2Client, list.id(), TestIdentities.emailOf(user2));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 still has access
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
    }

    @Test
    void shouldPreventOwnerFromUnsharingThemselves() {
        RestClient user1Client = restClient(user1);

        // User 1 creates a list
        ShoppingListListDto list = createShoppingList(user1Client, "Owner Self Unshare Test");

        // User 1 tries to unshare themselves - should fail
        try {
            unshareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user1));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }

    @Test
    void shouldRefuseDuplicateShare() {
        RestClient user1Client = restClient(user1);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Duplicate Share Test");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));

        // Share again while the first invite is still pending - refused, not silently ignored
        try {
            shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(409);
            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<>() {
            });
            assertThat(body.get("reason")).isEqualTo("ALREADY_INVITED");
        }

        // Verify still only owner + one pending invite, not two
        List<PermissionDto> permissions = getPermissions(user1Client, list.id());
        assertThat(permissions).hasSize(2);
    }

    @Test
    void shouldRejectShareAtOwnerRole() {
        RestClient user1Client = restClient(user1);

        ShoppingListListDto list = createShoppingList(user1Client, "Share At Owner Role Test");

        // OWNER cannot be granted through an invite - there is no ownership transfer
        try {
            user1Client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/share")
                    .body(new ShareRequest(TestIdentities.emailOf(user2), ResourceRole.OWNER))
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }

        assertThat(getPermissions(user1Client, list.id())).hasSize(1);
    }

    @Test
    void shouldHandleUnshareNonExistentAsNoOp() {
        RestClient user1Client = restClient(user1);

        // User 1 creates a list
        ShoppingListListDto list = createShoppingList(user1Client, "Unshare Non-existent Test");

        // Unshare someone who doesn't have access - should be no-op
        unshareShoppingList(user1Client, list.id(), TestIdentities.emailOf("nonexistent"));

        // Verify still only 1 user
        List<PermissionDto> permissions = getPermissions(user1Client, list.id());
        assertThat(permissions).hasSize(1);
    }

    @Test
    void shouldAllowSharedUserToViewList() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates and shares with User 2, who accepts
        ShoppingListListDto list = createShoppingList(user1Client, "Shared Edit Test");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));
        acceptPendingShoppingListInvite(user2Client);

        // User 2 can access the list
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
        assertThat(sharedList.name()).isEqualTo("Shared Edit Test");
        assertThat(sharedList.role()).isEqualTo(ResourceRole.EDITOR);

        // User 1 can also see the list
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }

    @Test
    void shouldPreventSharedUserFromDeletingList() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        // User 1 creates and shares with User 2, who accepts
        ShoppingListListDto list = createShoppingList(user1Client, "Delete Permission Test");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));
        acceptPendingShoppingListInvite(user2Client);

        // User 2 (EDITOR) tries to delete - should fail
        try {
            deleteShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify list still exists
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }

    @Test
    void shouldCreateItemWithVersionZero() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", new BigDecimal("2.0"), "liters", new BigDecimal("1.0")));

        assertThat(item).isNotNull();
        assertThat(item.id()).isNotNull();
        assertThat(item.name()).isEqualTo("Milk");
        assertThat(item.quantity()).isEqualByComparingTo("2.0");
        assertThat(item.unit()).isEqualTo("liters");
        assertThat(item.checked()).isFalse();
        assertThat(item.position()).isEqualByComparingTo("1.000000000000");
        assertThat(item.version()).isEqualTo(0L);
    }

    @Test
    void shouldCreateItemAsCheckedWhenCheckedIsTrue() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", new BigDecimal("2.0"), "liters", true, new BigDecimal("1.0")));

        assertThat(item.checked()).isTrue();

        ShoppingListDto shoppingList = getShoppingList(client, list.id());
        assertThat(shoppingList.items())
                .filteredOn(i -> i.id().equals(item.id()))
                .extracting(ShoppingListItemDto::checked)
                .containsExactly(true);
    }

    @Test
    void shouldCreateItemAsUncheckedWhenCheckedIsOmitted() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        ShoppingListItemDto item = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/items")
                .body(Map.of("name", "Milk", "quantity", new BigDecimal("2.0"), "unit", "liters", "position", new BigDecimal("1.0")))
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(item.checked()).isFalse();
    }

    @Test
    void shouldShowCreatedItemInGetShoppingList() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("1.0")));

        ShoppingListDto shoppingList = getShoppingList(client, list.id());
        assertThat(shoppingList.items())
                .extracting(ShoppingListItemDto::id)
                .contains(item.id());
    }

    @Test
    void shouldAllowTwoItemsAtSamePositionOrderedById() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        ShoppingListItemDto item1 = createItem(client, list.id(), itemRequest("Eggs", null, null, new BigDecimal("5.0")));
        ShoppingListItemDto item2 = createItem(client, list.id(), itemRequest("Cheese", null, null, new BigDecimal("5.0")));

        ShoppingListDto shoppingList = getShoppingList(client, list.id());
        var ids = shoppingList.items().stream().map(ShoppingListItemDto::id).toList();

        assertThat(ids).containsExactlyInAnyOrder(item1.id(), item2.id());

        // ordering under a tied position is stable across repeated reads (tie-broken by id)
        ShoppingListDto shoppingListAgain = getShoppingList(client, list.id());
        assertThat(shoppingListAgain.items().stream().map(ShoppingListItemDto::id).toList()).isEqualTo(ids);
    }

    @Test
    void shouldReturn400WhenNameIsBlank() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        try {
            createItem(client, list.id(), itemRequest("", null, null, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldReturn400WhenQuantityIsNegative() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        try {
            createItem(client, list.id(), itemRequest("Milk", new BigDecimal("-1.0"), null, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldReturn400WhenPositionIsMissing() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        try {
            createItem(client, list.id(), itemRequest("Milk", null, null, null));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldAcceptNullQuantityAndUnit() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");

        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Napkins", null, null, new BigDecimal("1.0")));

        assertThat(item.quantity()).isNull();
        assertThat(item.unit()).isNull();
    }

    @Test
    void shouldReturn404WhenListDoesNotExistOnCreate() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            createItem(client, nonExistentId, itemRequest("Milk", null, null, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenCreatingItemWithoutPermission() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");

        try {
            createItem(user2Client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldAllowSharedEditorToCreateItem() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);

        ShoppingListListDto list = createShoppingList(user1Client, "Shared List");
        shareShoppingList(user1Client, list.id(), TestIdentities.emailOf(user2));
        acceptPendingShoppingListInvite(user2Client);

        ShoppingListItemDto item = createItem(user2Client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        assertThat(item).isNotNull();
        assertThat(item.name()).isEqualTo("Milk");
    }

    @Test
    void shouldUpdateItemWithBumpedVersion() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", new BigDecimal("1.0"), "liters", new BigDecimal("1.0")));

        ShoppingListItemDto updated = updateItem(client, list.id(), item.id(),
                updateRequest(item.version(), "Whole Milk", new BigDecimal("2.0"), "liters", true, new BigDecimal("2.0")));

        assertThat(updated.name()).isEqualTo("Whole Milk");
        assertThat(updated.quantity()).isEqualByComparingTo("2.0");
        assertThat(updated.checked()).isTrue();
        assertThat(updated.version()).isEqualTo(item.version() + 1);
    }

    @Test
    void shouldChainSequentialUpdatesUsingReturnedVersion() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        ShoppingListItemDto firstUpdate = updateItem(client, list.id(), item.id(),
                updateRequest(item.version(), "Milk 2%", null, null, false, new BigDecimal("1.0")));
        ShoppingListItemDto secondUpdate = updateItem(client, list.id(), item.id(),
                updateRequest(firstUpdate.version(), "Milk 1%", null, null, true, new BigDecimal("1.0")));

        assertThat(secondUpdate.name()).isEqualTo("Milk 1%");
        assertThat(secondUpdate.version()).isEqualTo(firstUpdate.version() + 1);
    }

    @Test
    void shouldReturn412WithWinningItemWhenBaseVersionIsStale() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        updateItem(client, list.id(), item.id(),
                updateRequest(item.version(), "Whole Milk", null, null, false, new BigDecimal("1.0")));

        try {
            updateItem(client, list.id(), item.id(),
                    updateRequest(item.version(), "Skim Milk", null, null, false, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
            ShoppingListItemDto winner = ex.getResponseBodyAs(ShoppingListItemDto.class);
            assertThat(winner).isNotNull();
            assertThat(winner.name()).isEqualTo("Whole Milk");
        }

        ShoppingListDto shoppingList = getShoppingList(client, list.id());
        assertThat(shoppingList.items())
                .filteredOn(i -> i.id().equals(item.id()))
                .extracting(ShoppingListItemDto::name)
                .containsExactly("Whole Milk");
    }

    @Test
    void shouldAllowMovingTwoDifferentItemsConcurrently() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item1 = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
        ShoppingListItemDto item2 = createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));

        ShoppingListItemDto updated1 = updateItem(client, list.id(), item1.id(),
                updateRequest(item1.version(), item1.name(), item1.quantity(), item1.unit(), item1.checked(), new BigDecimal("5.0")));
        ShoppingListItemDto updated2 = updateItem(client, list.id(), item2.id(),
                updateRequest(item2.version(), item2.name(), item2.quantity(), item2.unit(), item2.checked(), new BigDecimal("6.0")));

        assertThat(updated1.position()).isEqualByComparingTo("5.0");
        assertThat(updated2.position()).isEqualByComparingTo("6.0");
    }

    @Test
    void shouldReturn412WhenMovingSameItemConcurrentlyFromStaleVersion() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        updateItem(client, list.id(), item.id(),
                updateRequest(item.version(), item.name(), item.quantity(), item.unit(), item.checked(), new BigDecimal("5.0")));

        try {
            updateItem(client, list.id(), item.id(),
                    updateRequest(item.version(), item.name(), item.quantity(), item.unit(), item.checked(), new BigDecimal("6.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
        }
    }

    @Test
    void shouldReturn404WhenItemBelongsToDifferentList() {
        RestClient client = restClient();
        ShoppingListListDto list1 = createShoppingList(client, "List 1");
        ShoppingListListDto list2 = createShoppingList(client, "List 2");
        ShoppingListItemDto item = createItem(client, list1.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        try {
            updateItem(client, list2.id(), item.id(),
                    updateRequest(item.version(), "Milk", null, null, false, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenItemDoesNotExist() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        UUID nonExistentItemId = UUID.randomUUID();

        try {
            updateItem(client, list.id(), nonExistentItemId,
                    updateRequest(0, "Milk", null, null, false, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenUpdatingItemWithoutPermission() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");
        ShoppingListItemDto item = createItem(user1Client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        try {
            updateItem(user2Client, list.id(), item.id(),
                    updateRequest(item.version(), "Milk", null, null, false, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn400WhenUpdateValidationFails() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        try {
            updateItem(client, list.id(), item.id(),
                    updateRequest(item.version(), "", null, null, false, new BigDecimal("1.0")));
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldDeleteItemAtCurrentVersion() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        deleteItem(client, list.id(), item.id(), item.version());

        ShoppingListDto shoppingList = getShoppingList(client, list.id());
        assertThat(shoppingList.items())
                .extracting(ShoppingListItemDto::id)
                .doesNotContain(item.id());
    }

    @Test
    void shouldReturn412WithWinningItemWhenDeletingAfterConcurrentEdit() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        ShoppingListItemDto updated = updateItem(client, list.id(), item.id(),
                updateRequest(item.version(), "Whole Milk", null, null, false, new BigDecimal("1.0")));

        try {
            deleteItem(client, list.id(), item.id(), item.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
            ShoppingListItemDto winner = ex.getResponseBodyAs(ShoppingListItemDto.class);
            assertThat(winner).isNotNull();
            assertThat(winner.name()).isEqualTo("Whole Milk");
        }

        ShoppingListDto shoppingList = getShoppingList(client, list.id());
        assertThat(shoppingList.items())
                .extracting(ShoppingListItemDto::id)
                .contains(updated.id());
    }

    @Test
    void shouldReturn404WhenDeletingAlreadyDeletedItem() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        deleteItem(client, list.id(), item.id(), item.version());

        try {
            deleteItem(client, list.id(), item.id(), item.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn400WhenBaseVersionQueryParamIsMissing() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "Groceries");
        ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        try {
            client.delete()
                    .uri("/shopping-lists/" + list.id() + "/items/" + item.id())
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldReturn403WhenDeletingItemWithoutPermission() {
        RestClient user1Client = restClient(user1);
        RestClient user2Client = restClient(user2);
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");
        ShoppingListItemDto item = createItem(user1Client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        try {
            deleteItem(user2Client, list.id(), item.id(), item.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    private ResponseEntity<Void> getItemLimits(RestClient client, UUID listId) {
        return client.get()
                .uri("/shopping-lists/" + listId + "/limits")
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldReturn204ForItemLimitsWhenLimitsAreDisabled() {
        RestClient client = restClient();
        ShoppingListListDto list = createShoppingList(client, "List 1");

        ResponseEntity<Void> response = getItemLimits(client, list.id());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Nested
    @LimitsEnabled
    class LimitsEnforced {

        private String ownerSubject;

        @Autowired
        private LimitsFacade limitsFacade;

        @Autowired
        private JdbcClient jdbcClient;

        @Autowired
        private DataSource dataSource;

        @BeforeEach
        void seedOverride() {
            ownerSubject = TestIdentities.emailOf(owner);
            setLimitQuota("SHOPPING_LIST", ownerSubject, 2);
            setLimitQuota("SHOPPING_LIST_ITEM", ownerSubject, 3);
        }

        private int usedFor(String subject) {
            return limitsFacade.getBalance(subject, ShoppingListService.SHOPPING_LIST_RESOURCE)
                    .map(LimitBalance::used)
                    .orElse(0);
        }

        private int usedForItem(UUID listId) {
            return limitsFacade.getBalance(listId.toString(), ShoppingListService.SHOPPING_LIST_ITEM_RESOURCE)
                    .map(LimitBalance::used)
                    .orElse(0);
        }

        private void setLimitQuota(String resource, String subject, int maxValue) {
            setLimitQuota(resource, subject, "STOCK", maxValue);
        }

        /**
         * Upserts the quota: {@code limit_config} has no write API, so there is no business path to it.
         */
        private void setLimitQuota(String resource, String subject, String kind, int maxValue) {
            jdbcClient.sql("""
                            INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                            VALUES (:id, :resource, :subject, :kind, :maxValue, NULL)
                            ON CONFLICT (resource, subject) DO UPDATE SET
                                kind      = EXCLUDED.kind,
                                max_value = EXCLUDED.max_value
                            """)
                    .param("id", UUID.randomUUID())
                    .param("resource", resource)
                    .param("subject", subject)
                    .param("kind", kind)
                    .param("maxValue", maxValue)
                    .update();
        }

        @Test
        void shouldRefuseThirdCreateWithLimitDetails() {
            RestClient client = restClient();
            createShoppingList(client, "List 1");
            createShoppingList(client, "List 2");

            try {
                createShoppingList(client, "List 3");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                assertThat(ex.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(body).isNotNull();
                assertThat(body.get("resource")).isEqualTo("SHOPPING_LIST");
                assertThat(body.get("kind")).isEqualTo("STOCK");
                assertThat(body.get("limit")).isEqualTo(2);
                assertThat(body.get("used")).isEqualTo(2);
            }
        }

        @Test
        void shouldCarryNoRetryAfterOnStockRefusal() {
            RestClient client = restClient();
            createShoppingList(client, "List 1");
            createShoppingList(client, "List 2");

            try {
                createShoppingList(client, "List 3");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getResponseHeaders().get("Retry-After")).isNull();

                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(body).isNotNull();
                assertThat(body).doesNotContainKey("retryAfterSeconds");
            }
        }

        @Test
        void shouldAllowReadAndUpdateWhileOverQuotaButKeepCreationRefused() {
            RestClient client = restClient();
            ShoppingListListDto list1 = createShoppingList(client, "List 1");
            createShoppingList(client, "List 2");

            setLimitQuota("SHOPPING_LIST", ownerSubject, 1);

            ShoppingListDto fetched = getShoppingList(client, list1.id());
            assertThat(fetched.id()).isEqualTo(list1.id());

            ShoppingListListDto updated = updateShoppingList(client, list1.id(), "List 1 Updated");
            assertThat(updated.name()).isEqualTo("List 1 Updated");

            try {
                createShoppingList(client, "List 3");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }

        @Test
        void shouldAdmitNextCreateAndDropBalanceAfterDelete() {
            RestClient client = restClient();
            ShoppingListListDto list1 = createShoppingList(client, "List 1");
            createShoppingList(client, "List 2");
            assertThat(usedFor(ownerSubject)).isEqualTo(2);

            deleteShoppingList(client, list1.id());
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            createShoppingList(client, "List 3");
            assertThat(usedFor(ownerSubject)).isEqualTo(2);
        }

        @Test
        void shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare() {
            RestClient client = restClient();
            RestClient recipientClient = restClient(user2);
            ShoppingListListDto list = createShoppingList(client, "Shared List");
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            shareShoppingList(client, list.id(), TestIdentities.emailOf(user2));
            acceptPendingShoppingListInvite(recipientClient);
            assertThat(usedFor(TestIdentities.emailOf(user2))).isZero();

            unshareShoppingList(client, list.id(), TestIdentities.emailOf(user2));
            assertThat(usedFor(TestIdentities.emailOf(user2))).isZero();
        }

        @Test
        void shouldNotCountPendingInviteTowardsRecipientQuota() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "Shared List");
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            shareShoppingList(client, list.id(), TestIdentities.emailOf(user2));

            assertThat(usedFor(TestIdentities.emailOf(user2))).isZero();
            assertThat(usedFor(ownerSubject)).isEqualTo(1);
        }

        @Test
        void shouldNotMoveListBalanceWhenAddingAndDeletingItems() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List With Items");
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            deleteItem(client, list.id(), item.id(), item.version());
            assertThat(usedFor(ownerSubject)).isEqualTo(1);
        }

        @Test
        void shouldRepairDriftToActualOwnedCountViaRecompute() {
            RestClient client = restClient();
            createShoppingList(client, "List 1");
            createShoppingList(client, "List 2");
            assertThat(usedFor(ownerSubject)).isEqualTo(2);

            // Deliberate drift: no business path can move used away from the owned count.
            jdbcClient.sql("UPDATE recipai.limit_usage SET used = 99 WHERE resource = 'SHOPPING_LIST' AND subject = :subject")
                    .param("subject", ownerSubject)
                    .update();
            assertThat(usedFor(ownerSubject)).isEqualTo(99);

            RecomputeMigration.run(dataSource);

            assertThat(usedFor(ownerSubject)).isEqualTo(2);
        }

        @Test
        void shouldClearUsageForSubjectThatOwnsNothing() {
            String ghost = TestIdentities.emailOf(TestIdentities.freshToken());
            // A usage row for a subject that owns nothing: no business path leaves one behind.
            jdbcClient.sql("""
                            INSERT INTO recipai.limit_usage (resource, subject, used, period_start)
                            VALUES ('SHOPPING_LIST', :subject, 5, now())
                            """)
                    .param("subject", ghost)
                    .update();
            assertThat(usedFor(ghost)).isEqualTo(5);

            RecomputeMigration.run(dataSource);

            assertThat(limitsFacade.getBalance(ghost, ShoppingListService.SHOPPING_LIST_RESOURCE)).isEmpty();
        }

        @Test
        void shouldSpareFlowConfiguredSubjectFromRecompute() {
            RestClient client = restClient();
            setLimitQuota("SHOPPING_LIST", ownerSubject, "FLOW", 5);
            try {
                ShoppingListListDto first = createShoppingList(client, "Flow 1");
                createShoppingList(client, "Flow 2");
                // A flow release refunds nothing, so the balance stays at 2 while only one is owned.
                deleteShoppingList(client, first.id());
                assertThat(usedFor(ownerSubject)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(ownerSubject)).isEqualTo(2);
            } finally {
                setLimitQuota("SHOPPING_LIST", ownerSubject, 2);
                limitsFacade.clear(ownerSubject, ShoppingListService.SHOPPING_LIST_RESOURCE);
            }
        }

        @Test
        void shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow() {
            String defaultFlowSubject = TestIdentities.emailOf(user1);
            RestClient client = restClient(user1);
            setLimitQuota("SHOPPING_LIST", null, "FLOW", 5);
            try {
                ShoppingListListDto first = createShoppingList(client, "Default flow 1");
                createShoppingList(client, "Default flow 2");
                // A flow release refunds nothing, so the balance stays at 2 while only one is owned.
                deleteShoppingList(client, first.id());
                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);
            } finally {
                setLimitQuota("SHOPPING_LIST", null, "STOCK", 2);
                limitsFacade.clear(defaultFlowSubject, ShoppingListService.SHOPPING_LIST_RESOURCE);
            }
        }

        @Test
        void shouldChangeNothingOnSecondRecomputeRun() {
            RestClient client = restClient();
            createShoppingList(client, "List 1");

            RecomputeMigration.run(dataSource);
            int firstRun = usedFor(ownerSubject);

            RecomputeMigration.run(dataSource);
            int secondRun = usedFor(ownerSubject);

            assertThat(secondRun).isEqualTo(firstRun);
            assertThat(secondRun).isEqualTo(1);
        }

        @Test
        void shouldRefuseFourthItemWithLimitDetails() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            createItem(client, list.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));

            try {
                createItem(client, list.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                assertThat(ex.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
                assertThat(ex.getResponseHeaders().get("Retry-After")).isNull();

                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(body).isNotNull();
                assertThat(body.get("resource")).isEqualTo("SHOPPING_LIST_ITEM");
                assertThat(body.get("kind")).isEqualTo("STOCK");
                assertThat(body.get("limit")).isEqualTo(3);
                assertThat(body.get("used")).isEqualTo(3);
                assertThat(body).doesNotContainKey("retryAfterSeconds");
            }
        }

        @Test
        void shouldKeepItemCountsIndependentBetweenTwoListsOwnedByTheSameUser() {
            RestClient client = restClient();
            ShoppingListListDto listA = createShoppingList(client, "List A");
            ShoppingListListDto listB = createShoppingList(client, "List B");
            createItem(client, listA.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, listA.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            createItem(client, listA.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));

            try {
                createItem(client, listA.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }

            ShoppingListItemDto itemB = createItem(client, listB.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            assertThat(itemB).isNotNull();
            assertThat(usedForItem(listA.id())).isEqualTo(3);
            assertThat(usedForItem(listB.id())).isEqualTo(1);
        }

        @Test
        void shouldAdmitNextItemAfterDeletingOne() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            ShoppingListItemDto item3 = createItem(client, list.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));
            assertThat(usedForItem(list.id())).isEqualTo(3);

            deleteItem(client, list.id(), item3.id(), item3.version());
            assertThat(usedForItem(list.id())).isEqualTo(2);

            ShoppingListItemDto item4 = createItem(client, list.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
            assertThat(item4).isNotNull();
            assertThat(usedForItem(list.id())).isEqualTo(3);
        }

        @Test
        void shouldNotReleaseWhenItemDeleteIsRefused() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            assertThat(usedForItem(list.id())).isEqualTo(1);

            try {
                deleteItem(client, list.id(), item.id(), item.version() + 99);
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(412);
            }
            assertThat(usedForItem(list.id())).isEqualTo(1);

            try {
                deleteItem(client, list.id(), UUID.randomUUID(), 0);
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(404);
            }
            assertThat(usedForItem(list.id())).isEqualTo(1);
        }

        @Test
        void shouldNotChargeItemBudgetOnUpdate() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            ShoppingListItemDto item = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            assertThat(usedForItem(list.id())).isEqualTo(1);

            ShoppingListItemDto updated = updateItem(client, list.id(), item.id(),
                    updateRequest(item.version(), "Oat milk", new BigDecimal("2.0"), "l", true, new BigDecimal("1.5")));
            assertThat(updated.name()).isEqualTo("Oat milk");
            assertThat(usedForItem(list.id())).isEqualTo(1);
        }

        @Test
        void shouldAllowReadAndEditWhileOverQuotaButKeepItemCreationRefused() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            ShoppingListItemDto item1 = createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            createItem(client, list.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));

            setLimitQuota("SHOPPING_LIST_ITEM", ownerSubject, 1);

            ShoppingListDto fetched = getShoppingList(client, list.id());
            assertThat(fetched.items()).hasSize(3);

            ShoppingListItemDto edited = updateItem(client, list.id(), item1.id(),
                    updateRequest(item1.version(), "Oat milk", new BigDecimal("2.0"), "l", true, new BigDecimal("1.5")));
            assertThat(edited.name()).isEqualTo("Oat milk");

            try {
                createItem(client, list.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }

        @Test
        void shouldRaiseQuotaOnEveryListOwnedByTheUserWhenOverrideIsRaised() {
            RestClient client = restClient();
            ShoppingListListDto listA = createShoppingList(client, "List A");
            ShoppingListListDto listB = createShoppingList(client, "List B");
            for (ShoppingListListDto list : List.of(listA, listB)) {
                createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
                createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
                createItem(client, list.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));
            }

            for (ShoppingListListDto list : List.of(listA, listB)) {
                try {
                    createItem(client, list.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
                    fail("Should have thrown exception");
                } catch (RestClientResponseException ex) {
                    assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                }
            }

            setLimitQuota("SHOPPING_LIST_ITEM", ownerSubject, 4);

            ShoppingListItemDto itemA = createItem(client, listA.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
            ShoppingListItemDto itemB = createItem(client, listB.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
            assertThat(itemA).isNotNull();
            assertThat(itemB).isNotNull();
            assertThat(usedForItem(listA.id())).isEqualTo(4);
            assertThat(usedForItem(listB.id())).isEqualTo(4);
        }

        @Test
        void shouldApplyRaisedOverrideToAListCreatedAfterTheOverrideWasWritten() {
            RestClient client = restClient();
            ShoppingListListDto list1 = createShoppingList(client, "List 1");
            createItem(client, list1.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, list1.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            createItem(client, list1.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));

            setLimitQuota("SHOPPING_LIST_ITEM", ownerSubject, 4);

            deleteShoppingList(client, list1.id());

            ShoppingListListDto list2 = createShoppingList(client, "List 2");
            createItem(client, list2.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, list2.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            createItem(client, list2.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));
            ShoppingListItemDto item4 = createItem(client, list2.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));

            assertThat(item4).isNotNull();
            assertThat(usedForItem(list2.id())).isEqualTo(4);
        }

        @Test
        void shouldChargeTheListAndResolveTheOwnersQuotaWhenAnEditorAddsAnItem() {
            RestClient ownerClient = restClient();
            RestClient editorClient = restClient(user2);
            setLimitQuota("SHOPPING_LIST_ITEM", TestIdentities.emailOf(user2), 10);

            ShoppingListListDto list = createShoppingList(ownerClient, "Shared List");
            shareShoppingList(ownerClient, list.id(), TestIdentities.emailOf(user2));
            acceptPendingShoppingListInvite(editorClient);

            createItem(ownerClient, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(ownerClient, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));

            ShoppingListItemDto item3 = createItem(editorClient, list.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));
            assertThat(item3).isNotNull();
            assertThat(usedForItem(list.id())).isEqualTo(3);

            try {
                createItem(editorClient, list.id(), itemRequest("Butter", null, null, new BigDecimal("4.0")));
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }

            assertThat(limitsFacade.getBalance(TestIdentities.emailOf(user2), ShoppingListService.SHOPPING_LIST_ITEM_RESOURCE)).isEmpty();
        }

        @Test
        void shouldClearItemUsageWhenTheListIsDeleted() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            assertThat(usedForItem(list.id())).isEqualTo(1);

            deleteShoppingList(client, list.id());

            assertThat(limitsFacade.getBalance(list.id().toString(), ShoppingListService.SHOPPING_LIST_ITEM_RESOURCE)).isEmpty();
        }

        @Test
        void shouldReproducePerListItemCountsViaRecompute() {
            RestClient client = restClient();
            ShoppingListListDto listA = createShoppingList(client, "List A");
            ShoppingListListDto listB = createShoppingList(client, "List B");
            createItem(client, listA.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
            createItem(client, listA.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
            createItem(client, listB.id(), itemRequest("Eggs", null, null, new BigDecimal("3.0")));
            assertThat(usedForItem(listA.id())).isEqualTo(2);
            assertThat(usedForItem(listB.id())).isEqualTo(1);

            // Deliberate drift: no business path can move used away from the list's item count.
            jdbcClient.sql("UPDATE recipai.limit_usage SET used = 99 WHERE resource = 'SHOPPING_LIST_ITEM' AND subject = :subject")
                    .param("subject", listA.id().toString())
                    .update();
            assertThat(usedForItem(listA.id())).isEqualTo(99);

            RecomputeMigration.run(dataSource);

            assertThat(usedForItem(listA.id())).isEqualTo(2);
            assertThat(usedForItem(listB.id())).isEqualTo(1);
        }

        @Test
        void shouldChangeNothingOnSecondItemRecomputeRun() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");
            createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

            RecomputeMigration.run(dataSource);
            int firstRun = usedForItem(list.id());

            RecomputeMigration.run(dataSource);
            int secondRun = usedForItem(list.id());

            assertThat(secondRun).isEqualTo(firstRun);
            assertThat(secondRun).isEqualTo(1);
        }

        @Test
        void shouldSpareListWhoseOwnerIsFlowConfiguredFromItemRecompute() {
            RestClient client = restClient();
            setLimitQuota("SHOPPING_LIST_ITEM", ownerSubject, "FLOW", 5);
            try {
                ShoppingListListDto list = createShoppingList(client, "List 1");
                createItem(client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));
                ShoppingListItemDto second = createItem(client, list.id(), itemRequest("Bread", null, null, new BigDecimal("2.0")));
                // A flow release refunds nothing, so the balance stays at 2 while the list holds one.
                deleteItem(client, list.id(), second.id(), second.version());
                assertThat(usedForItem(list.id())).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedForItem(list.id())).isEqualTo(2);
            } finally {
                setLimitQuota("SHOPPING_LIST_ITEM", ownerSubject, 3);
            }
        }

        private Map<String, Object> getListBalance(RestClient client) {
            return client.get()
                    .uri("/shopping-lists/balance")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        }

        private Map<String, Object> getItemQuota(RestClient client, UUID listId) {
            return client.get()
                    .uri("/shopping-lists/" + listId + "/limits")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        }

        @Test
        void shouldTrackListUsageAcrossCreateAndDelete() {
            RestClient client = restClient();
            assertThat(getListBalance(client).get("used")).isEqualTo(0);

            ShoppingListListDto list = createShoppingList(client, "List 1");
            assertThat(getListBalance(client).get("used")).isEqualTo(1);

            deleteShoppingList(client, list.id());
            assertThat(getListBalance(client).get("used")).isEqualTo(0);
        }

        @Test
        void shouldReturnItemQuotaConfiguredAgainstOwnerWhenReadByOwner() {
            RestClient client = restClient();
            ShoppingListListDto list = createShoppingList(client, "List 1");

            Map<String, Object> quota = getItemQuota(client, list.id());

            assertThat(quota.get("resource")).isEqualTo("SHOPPING_LIST_ITEM");
            assertThat(quota.get("limit")).isEqualTo(3);
        }

        @Test
        void shouldReturnOwnerConfiguredQuotaWhenReadBySharedEditorNotEditorsOwnOverride() {
            RestClient owner = restClient();
            RestClient editor = restClient(user1);
            ShoppingListListDto list = createShoppingList(owner, "List 1");
            shareShoppingList(owner, list.id(), TestIdentities.emailOf(user1));
            acceptPendingShoppingListInvite(editor);
            setLimitQuota("SHOPPING_LIST_ITEM", TestIdentities.emailOf(user1), 99);

            Map<String, Object> quota = getItemQuota(editor, list.id());

            assertThat(quota.get("limit")).isEqualTo(3);
        }

        @Test
        void shouldReturn403ForUserWithNoPermissionOnList() {
            RestClient owner = restClient();
            RestClient stranger = restClient(user2);
            ShoppingListListDto list = createShoppingList(owner, "List 1");

            try {
                getItemQuota(stranger, list.id());
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(403);
            }
        }

        @Test
        void shouldReturn404ForUnknownListId() {
            RestClient client = restClient();

            try {
                getItemQuota(client, UUID.randomUUID());
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(404);
            }
        }
    }
}

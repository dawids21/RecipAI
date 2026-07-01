package xyz.stasiak.recipai.shoppinglists;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.shoppinglists.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShoppingListIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return restClient(TestSecurityConfiguration.AUTH_TOKEN);
    }

    private RestClient restClient(String authToken) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();
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
        ShareShoppingListRequest request = new ShareShoppingListRequest(email);
        client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareShoppingList(RestClient client, UUID shoppingListId, String email) {
        UnshareShoppingListRequest request = new UnshareShoppingListRequest(email);
        client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<SharedUserDto> getSharedUsers(RestClient client, UUID shoppingListId) {
        return client
                .get()
                .uri("/shopping-lists/" + shoppingListId + "/users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
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
        return new CreateShoppingListItemRequest(name, quantity, unit, position);
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
        assertThat(response.role()).isEqualTo(UserRole.OWNER);
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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

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
            assertThat(responseBody).contains("Access denied to shopping list with id: " + user1List.id());
            assertThat(responseBody).contains("Shopping List Access Denied");
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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

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

        // User 1 shares with User 2
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can now access the list
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
        assertThat(sharedList.name()).isEqualTo("Shared List");
        assertThat(sharedList.role()).isEqualTo(UserRole.EDITOR);

        // Verify shared users list
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
        assertThat(sharedUsers)
                .extracting(SharedUserDto::email)
                .containsExactly("user1@example.com", "user2@example.com");
        assertThat(sharedUsers)
                .extracting(SharedUserDto::role)
                .containsExactly(UserRole.OWNER, UserRole.EDITOR);

        // User 1 unshares from User 2
        unshareShoppingList(user1Client, list.id(), "user2@example.com");

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Editor Share Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 (EDITOR) can share with another user
        shareShoppingList(user2Client, list.id(), "user@example.com");

        // Verify three users have access
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(3);

        // User 2 (EDITOR) can unshare
        unshareShoppingList(user2Client, list.id(), "user@example.com");

        // Verify only two users remain
        sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
    }

    @Test
    void shouldPreventUnsharingOwner() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Unshare Owner Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 tries to unshare User 1 (OWNER) - should fail
        try {
            unshareShoppingList(user2Client, list.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
        assertThat(sharedUsers)
                .extracting(SharedUserDto::email)
                .contains("user1@example.com");
    }

    @Test
    void shouldAllowEditorToUnshareThemselves() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Self Unshare Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can unshare themselves
        unshareShoppingList(user2Client, list.id(), "user2@example.com");

        // User 2 can no longer access
        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldPreventOwnerFromUnsharingThemselves() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates a list
        ShoppingListListDto list = createShoppingList(user1Client, "Owner Self Unshare Test");

        // User 1 tries to unshare themselves - should fail
        try {
            unshareShoppingList(user1Client, list.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }

    @Test
    void shouldHandleDuplicateShareAsNoOp() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Duplicate Share Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // Share again - should be no-op
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // Verify still only 2 users
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
    }

    @Test
    void shouldHandleUnshareNonExistentAsNoOp() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates a list
        ShoppingListListDto list = createShoppingList(user1Client, "Unshare Non-existent Test");

        // Unshare someone who doesn't have access - should be no-op
        unshareShoppingList(user1Client, list.id(), "nonexistent@example.com");

        // Verify still only 1 user
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(1);
    }

    @Test
    void shouldAllowSharedUserToViewList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Shared Edit Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can access the list
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
        assertThat(sharedList.name()).isEqualTo("Shared Edit Test");
        assertThat(sharedList.role()).isEqualTo(UserRole.EDITOR);

        // User 1 can also see the list
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }

    @Test
    void shouldPreventSharedUserFromDeletingList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Delete Permission Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        ShoppingListListDto list = createShoppingList(user1Client, "Shared List");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);
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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");
        ShoppingListItemDto item = createItem(user1Client, list.id(), itemRequest("Milk", null, null, new BigDecimal("1.0")));

        try {
            deleteItem(user2Client, list.id(), item.id(), item.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }
}

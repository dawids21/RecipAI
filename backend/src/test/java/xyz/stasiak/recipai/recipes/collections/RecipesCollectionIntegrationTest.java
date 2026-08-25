package xyz.stasiak.recipai.recipes.collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.RecomputeMigration;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.recipes.collections.dto.*;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "recipai.limits.enabled=false")
class RecipesCollectionIntegrationTest {

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

    private RecipesCollectionListDto createRecipesCollection(RestClient client, String name) {
        CreateRecipesCollectionRequest request = new CreateRecipesCollectionRequest(name);
        return client
                .post()
                .uri("/collections")
                .body(request)
                .retrieve()
                .body(RecipesCollectionListDto.class);
    }

    private List<RecipesCollectionListDto> getAllRecipesCollections(RestClient client) {
        return client
                .get()
                .uri("/collections")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private RecipesCollectionListDto updateRecipesCollection(RestClient client, UUID id, String newName) {
        UpdateRecipesCollectionRequest request = new UpdateRecipesCollectionRequest(newName);
        return client
                .put()
                .uri("/collections/" + id)
                .body(request)
                .retrieve()
                .body(RecipesCollectionListDto.class);
    }

    private void deleteRecipesCollection(RestClient client, UUID id) {
        client
                .delete()
                .uri("/collections/" + id)
                .retrieve()
                .toBodilessEntity();
    }

    private void shareRecipesCollection(RestClient client, UUID recipesCollectionId, String email) {
        ShareRecipesCollectionRequest request = new ShareRecipesCollectionRequest(email);
        client
                .post()
                .uri("/collections/" + recipesCollectionId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareRecipesCollection(RestClient client, UUID recipesCollectionId, String email) {
        UnshareRecipesCollectionRequest request = new UnshareRecipesCollectionRequest(email);
        client
                .post()
                .uri("/collections/" + recipesCollectionId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<SharedUserDto> getSharedUsers(RestClient client, UUID recipesCollectionId) {
        return client
                .get()
                .uri("/collections/" + recipesCollectionId + "/users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void shouldCreateRecipesCollection() {
        RestClient client = restClient();

        RecipesCollectionListDto response = createRecipesCollection(client, "My Recipes");

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("My Recipes");
    }

    @Test
    void shouldGetAllRecipesCollections() {
        RestClient client = restClient();

        // Create two collections
        createRecipesCollection(client, "Italian Recipes");
        createRecipesCollection(client, "Asian Recipes");

        // Get all collections
        List<RecipesCollectionListDto> collections = getAllRecipesCollections(client);

        assertThat(collections).isNotEmpty();
        assertThat(collections)
                .extracting(RecipesCollectionListDto::name)
                .contains("Italian Recipes", "Asian Recipes");
    }

    @Test
    void shouldUpdateRecipesCollection() {
        RestClient client = restClient();

        // Create collection
        RecipesCollectionListDto created = createRecipesCollection(client, "Old Name");

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("Old Name");

        // Update collection
        RecipesCollectionListDto updated = updateRecipesCollection(client, created.id(), "New Name");

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("New Name");
    }

    @Test
    void shouldDeleteRecipesCollection() {
        RestClient client = restClient();

        // Create collection
        RecipesCollectionListDto created = createRecipesCollection(client, "To Be Deleted");

        assertThat(created).isNotNull();

        // Delete collection
        deleteRecipesCollection(client, created.id());

        // Verify deleted by trying to get all collections - should not contain deleted one
        List<RecipesCollectionListDto> collections = getAllRecipesCollections(client);
        assertThat(collections)
                .extracting(RecipesCollectionListDto::id)
                .doesNotContain(created.id());
    }

    @Test
    void shouldReturn404WhenRecipesCollectionNotFound() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            updateRecipesCollection(client, nonExistentId, "New Name");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Recipes collection not found with id: " + nonExistentId);
            assertThat(responseBody).contains("Recipes Collection Not Found");
        }
    }

    @Test
    void shouldReturn403WhenAccessingOthersRecipesCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a collection
        RecipesCollectionListDto user1Collection = createRecipesCollection(user1Client, "User 1 Collection");

        assertThat(user1Collection).isNotNull();

        // User 2 tries to delete User 1's collection - should get 403 Forbidden
        try {
            deleteRecipesCollection(user2Client, user1Collection.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied to recipes collection with id: " + user1Collection.id());
            assertThat(responseBody).contains("Recipes Collection Access Denied");
        }
    }

    @Test
    void shouldShareAndUnshareRecipesCollections() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipes collection
        RecipesCollectionListDto collection = createRecipesCollection(user1Client, "Shared Collection");
        assertThat(collection).isNotNull();

        // User 2 cannot access initially
        try {
            deleteRecipesCollection(user2Client, collection.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 shares with User 2
        shareRecipesCollection(user1Client, collection.id(), "user2@example.com");

        // Verify shared users list
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, collection.id());
        assertThat(sharedUsers).hasSize(2);
        assertThat(sharedUsers)
                .extracting(SharedUserDto::email)
                .containsExactly("user1@example.com", "user2@example.com");
        assertThat(sharedUsers)
                .extracting(SharedUserDto::role)
                .containsExactly(UserRole.OWNER, UserRole.EDITOR);

        // User 1 unshares from User 2
        unshareRecipesCollection(user1Client, collection.id(), "user2@example.com");

        // User 2 can no longer access
        try {
            deleteRecipesCollection(user2Client, collection.id());
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
        RecipesCollectionListDto collection = createRecipesCollection(user1Client, "Editor Share Test");
        shareRecipesCollection(user1Client, collection.id(), "user2@example.com");

        // User 2 (EDITOR) can share with another user
        shareRecipesCollection(user2Client, collection.id(), "user@example.com");

        // Verify three users have access
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, collection.id());
        assertThat(sharedUsers).hasSize(3);

        // User 2 (EDITOR) can unshare
        unshareRecipesCollection(user2Client, collection.id(), "user@example.com");

        // Verify only two users remain
        sharedUsers = getSharedUsers(user1Client, collection.id());
        assertThat(sharedUsers).hasSize(2);
    }

    @Test
    void shouldPreventUnsharingOwner() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        RecipesCollectionListDto collection = createRecipesCollection(user1Client, "Unshare Owner Test");
        shareRecipesCollection(user1Client, collection.id(), "user2@example.com");

        // User 2 tries to unshare User 1 (OWNER) - should fail
        try {
            unshareRecipesCollection(user2Client, collection.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, collection.id());
        assertThat(sharedUsers).hasSize(2);
        assertThat(sharedUsers)
                .extracting(SharedUserDto::email)
                .contains("user1@example.com");
    }

    @Test
    void shouldBeIdempotentWhenSharingTwice() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates and shares with User 2
        RecipesCollectionListDto collection = createRecipesCollection(user1Client, "Duplicate Share Test");
        shareRecipesCollection(user1Client, collection.id(), "user2@example.com");

        // Share again - should be no-op
        shareRecipesCollection(user1Client, collection.id(), "user2@example.com");

        // Verify still only 2 users
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, collection.id());
        assertThat(sharedUsers).hasSize(2);
    }

    @Nested
    @TestPropertySource(properties = "recipai.limits.enabled=true")
    class LimitsEnforced {

        private static final String SUBJECT = "user@example.com";

        @Autowired
        private LimitsFacade limitsFacade;

        @Autowired
        private JdbcClient jdbcClient;

        @Autowired
        private DataSource dataSource;

        @BeforeEach
        void setUpQuota() {
            setLimitQuota("RECIPES_COLLECTION", SUBJECT, 2);
        }

        @AfterEach
        void tearDown() {
            for (String token : List.of(
                    TestSecurityConfiguration.AUTH_TOKEN,
                    TestSecurityConfiguration.AUTH_TOKEN_USER_1,
                    TestSecurityConfiguration.AUTH_TOKEN_USER_2)) {
                RestClient client = restClient(token);
                for (RecipesCollectionListDto collection : getAllRecipesCollections(client)) {
                    try {
                        deleteRecipesCollection(client, collection.id());
                    } catch (RestClientResponseException ignored) {
                        // not the owner, ignore
                    }
                }
            }

            // Teardown of rows no API deletes: the config override, and usage fabricated for subjects
            // with no API presence.
            jdbcClient.sql("DELETE FROM recipai.limit_config WHERE resource = 'RECIPES_COLLECTION' AND subject IS NOT NULL").update();
            jdbcClient.sql("""
                            DELETE FROM recipai.limit_usage
                             WHERE resource = 'RECIPES_COLLECTION' AND subject NOT IN (:subject, :user1, :user2)
                            """)
                    .param("subject", SUBJECT)
                    .param("user1", "user1@example.com")
                    .param("user2", "user2@example.com")
                    .update();

            assertThat(usedFor(SUBJECT)).isZero();
        }

        private int usedFor(String subject) {
            return limitsFacade.getBalance(subject, RecipesCollectionService.RECIPES_COLLECTION_RESOURCE)
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
            createRecipesCollection(client, "Collection 1");
            createRecipesCollection(client, "Collection 2");

            try {
                createRecipesCollection(client, "Collection 3");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                assertThat(ex.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(body).isNotNull();
                assertThat(body.get("resource")).isEqualTo("RECIPES_COLLECTION");
                assertThat(body.get("kind")).isEqualTo("STOCK");
                assertThat(body.get("limit")).isEqualTo(2);
                assertThat(body.get("used")).isEqualTo(2);
            }
        }

        @Test
        void shouldCarryNoRetryAfterOnStockRefusal() {
            RestClient client = restClient();
            createRecipesCollection(client, "Collection 1");
            createRecipesCollection(client, "Collection 2");

            try {
                createRecipesCollection(client, "Collection 3");
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
            RecipesCollectionListDto collection1 = createRecipesCollection(client, "Collection 1");
            createRecipesCollection(client, "Collection 2");

            setLimitQuota("RECIPES_COLLECTION", SUBJECT, 1);

            List<RecipesCollectionListDto> collections = getAllRecipesCollections(client);
            assertThat(collections).extracting(RecipesCollectionListDto::id).contains(collection1.id());

            RecipesCollectionListDto updated = updateRecipesCollection(client, collection1.id(), "Collection 1 Updated");
            assertThat(updated.name()).isEqualTo("Collection 1 Updated");

            try {
                createRecipesCollection(client, "Collection 3");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }

        @Test
        void shouldAdmitNextCreateAndDropBalanceAfterDelete() {
            RestClient client = restClient();
            RecipesCollectionListDto collection1 = createRecipesCollection(client, "Collection 1");
            createRecipesCollection(client, "Collection 2");
            assertThat(usedFor(SUBJECT)).isEqualTo(2);

            deleteRecipesCollection(client, collection1.id());
            assertThat(usedFor(SUBJECT)).isEqualTo(1);

            createRecipesCollection(client, "Collection 3");
            assertThat(usedFor(SUBJECT)).isEqualTo(2);
        }

        @Test
        void shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare() {
            RestClient client = restClient();
            RecipesCollectionListDto collection = createRecipesCollection(client, "Shared Collection");
            assertThat(usedFor(SUBJECT)).isEqualTo(1);

            shareRecipesCollection(client, collection.id(), "user2@example.com");
            assertThat(usedFor("user2@example.com")).isZero();

            unshareRecipesCollection(client, collection.id(), "user2@example.com");
            assertThat(usedFor("user2@example.com")).isZero();
        }

        @Test
        void shouldRepairDriftToActualOwnedCountViaRecompute() {
            RestClient client = restClient();
            createRecipesCollection(client, "Collection 1");
            createRecipesCollection(client, "Collection 2");
            assertThat(usedFor(SUBJECT)).isEqualTo(2);

            // Deliberate drift: no business path can move used away from the owned count.
            jdbcClient.sql("UPDATE recipai.limit_usage SET used = 99 WHERE resource = 'RECIPES_COLLECTION' AND subject = :subject")
                    .param("subject", SUBJECT)
                    .update();
            assertThat(usedFor(SUBJECT)).isEqualTo(99);

            RecomputeMigration.run(dataSource);

            assertThat(usedFor(SUBJECT)).isEqualTo(2);
        }

        @Test
        void shouldClearUsageForSubjectThatOwnsNothing() {
            String ghost = "ghost@example.com";
            // A usage row for a subject that owns nothing: no business path leaves one behind.
            jdbcClient.sql("""
                            INSERT INTO recipai.limit_usage (resource, subject, used, period_start)
                            VALUES ('RECIPES_COLLECTION', :subject, 5, now())
                            """)
                    .param("subject", ghost)
                    .update();
            assertThat(usedFor(ghost)).isEqualTo(5);

            RecomputeMigration.run(dataSource);

            assertThat(limitsFacade.getBalance(ghost, RecipesCollectionService.RECIPES_COLLECTION_RESOURCE)).isEmpty();
        }

        @Test
        void shouldSpareFlowConfiguredSubjectFromRecompute() {
            RestClient client = restClient();
            setLimitQuota("RECIPES_COLLECTION", SUBJECT, "FLOW", 5);
            try {
                RecipesCollectionListDto first = createRecipesCollection(client, "Flow 1");
                createRecipesCollection(client, "Flow 2");
                // A flow release refunds nothing, so the balance stays at 2 while only one is owned.
                deleteRecipesCollection(client, first.id());
                assertThat(usedFor(SUBJECT)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(SUBJECT)).isEqualTo(2);
            } finally {
                setLimitQuota("RECIPES_COLLECTION", SUBJECT, 2);
                limitsFacade.clear(SUBJECT, RecipesCollectionService.RECIPES_COLLECTION_RESOURCE);
            }
        }

        @Test
        void shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow() {
            String defaultFlowSubject = "user1@example.com";
            RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
            setLimitQuota("RECIPES_COLLECTION", null, "FLOW", 5);
            try {
                RecipesCollectionListDto first = createRecipesCollection(client, "Flow 1");
                createRecipesCollection(client, "Flow 2");
                // A flow release refunds nothing, so the balance stays at 2 while only one is owned.
                deleteRecipesCollection(client, first.id());
                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);
            } finally {
                setLimitQuota("RECIPES_COLLECTION", null, "STOCK", 2);
                limitsFacade.clear(defaultFlowSubject, RecipesCollectionService.RECIPES_COLLECTION_RESOURCE);
            }
        }

        @Test
        void shouldChangeNothingOnSecondRecomputeRun() {
            RestClient client = restClient();
            createRecipesCollection(client, "Collection 1");

            RecomputeMigration.run(dataSource);
            int firstRun = usedFor(SUBJECT);

            RecomputeMigration.run(dataSource);
            int secondRun = usedFor(SUBJECT);

            assertThat(secondRun).isEqualTo(firstRun);
            assertThat(secondRun).isEqualTo(1);
        }

        private Map<String, Object> getBalance(RestClient client) {
            return client.get()
                    .uri("/collections/balance")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        }

        @Test
        void shouldTrackUsageAcrossCreateAndDelete() {
            RestClient client = restClient();
            assertThat(getBalance(client).get("used")).isEqualTo(0);

            RecipesCollectionListDto collection1 = createRecipesCollection(client, "Collection 1");
            createRecipesCollection(client, "Collection 2");
            assertThat(getBalance(client).get("used")).isEqualTo(2);

            deleteRecipesCollection(client, collection1.id());
            assertThat(getBalance(client).get("used")).isEqualTo(1);
        }
    }
}
package xyz.stasiak.recipai.permissions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.exception.InvalidInviteRoleException;
import xyz.stasiak.recipai.permissions.exception.InviteRefusedException;
import xyz.stasiak.recipai.permissions.exception.ResourceAccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "recipai.limits.enabled=false")
class InviteIntegrationTest {

    // Opaque keys no module owns: the module holds no domain knowledge and never inspects them.
    // Two distinct types so a test can prove /invites mixes resource types in one response without
    // reaching into another module's resource type.
    private static final String RESOURCE_TYPE = "INVITE_TEST_RESOURCE";
    private static final String OTHER_RESOURCE_TYPE = "INVITE_TEST_RESOURCE_OTHER";

    private static final String SHARER = "user1@example.com";
    private static final String INVITEE = "user2@example.com";
    private static final String STRANGER = "user@example.com";

    @LocalServerPort
    private int port;

    @Autowired
    private PermissionsFacade permissionsFacade;

    private record TrackedResource(String resourceType, UUID resourceId) {
    }

    private final List<TrackedResource> createdResources = new ArrayList<>();

    private RestClient restClient(String authToken) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();
    }

    private RestClient inviteeClient() {
        return restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);
    }

    // Every resource is owned by the sharer; reporting it deleted cascades to its permissions and
    // pending invites, resetting the invitee's and stranger's inboxes for the next test.
    @AfterEach
    void tearDown() {
        for (TrackedResource resource : createdResources) {
            permissionsFacade.resourceDeleted(resource.resourceType(), resource.resourceId());
        }
        createdResources.clear();
    }

    private UUID ownedResource() {
        return ownedResource(RESOURCE_TYPE);
    }

    private UUID ownedResource(String resourceType) {
        UUID resourceId = UUID.randomUUID();
        permissionsFacade.grantOwner(resourceType, resourceId, SHARER);
        createdResources.add(new TrackedResource(resourceType, resourceId));
        return resourceId;
    }

    private UUID invite(UUID resourceId, String label, String targetEmail) {
        return invite(RESOURCE_TYPE, resourceId, label, targetEmail);
    }

    private UUID invite(String resourceType, UUID resourceId, String label, String targetEmail) {
        return permissionsFacade.invite(resourceType, resourceId, targetEmail, ResourceRole.EDITOR, label, SHARER);
    }

    private List<PendingInviteDto> getPendingInvites(RestClient client) {
        return client.get()
                .uri("/invites")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void acceptInvite(RestClient client, UUID inviteId) {
        client.post()
                .uri("/invites/" + inviteId + "/accept")
                .retrieve()
                .toBodilessEntity();
    }

    private void declineInvite(RestClient client, UUID inviteId) {
        client.post()
                .uri("/invites/" + inviteId + "/decline")
                .retrieve()
                .toBodilessEntity();
    }

    private PendingInviteDto onlyPendingInviteFor(RestClient client) {
        List<PendingInviteDto> invites = getPendingInvites(client);
        assertThat(invites).hasSize(1);
        return invites.get(0);
    }

    @Test
    void shouldNotGrantAnyAccessWhileInviteIsPending() {
        UUID resourceId = ownedResource();

        invite(resourceId, "Pending Access Test", INVITEE);

        assertThat(permissionsFacade.roleOf(RESOURCE_TYPE, resourceId, INVITEE)).isEmpty();
        assertThat(permissionsFacade.accessibleResources(RESOURCE_TYPE, INVITEE)).doesNotContainKey(resourceId);
        assertThatExceptionOfType(ResourceAccessDeniedException.class)
                .isThrownBy(() -> permissionsFacade.requireEditor(RESOURCE_TYPE, resourceId, INVITEE));
    }

    @Test
    void shouldListPendingInviteWithLabelAndSender() {
        UUID resourceId = ownedResource();

        invite(resourceId, "My Groceries", INVITEE);

        PendingInviteDto invite = onlyPendingInviteFor(inviteeClient());
        assertThat(invite.resourceType()).isEqualTo(RESOURCE_TYPE);
        assertThat(invite.label()).isEqualTo("My Groceries");
        assertThat(invite.invitedBy()).isEqualTo(SHARER);
        assertThat(invite.role()).isEqualTo(ResourceRole.EDITOR);
    }

    @Test
    void shouldGrantAccessWhenInviteIsAccepted() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Accept Test", INVITEE);

        acceptInvite(invitee, inviteId);

        assertThat(permissionsFacade.roleOf(RESOURCE_TYPE, resourceId, INVITEE)).contains(ResourceRole.EDITOR);
        assertThat(permissionsFacade.accessibleResources(RESOURCE_TYPE, INVITEE))
                .containsEntry(resourceId, ResourceRole.EDITOR);
    }

    @Test
    void shouldRemoveInviteFromBothSidesWhenAccepted() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Remove From Both Sides Test", INVITEE);

        acceptInvite(invitee, inviteId);

        assertThat(getPendingInvites(invitee)).isEmpty();
        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId))
                .contains(new PermissionDto(INVITEE, ResourceRole.EDITOR, false));
    }

    @Test
    void shouldLeaveResourceInvisibleWhenInviteIsDeclined() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Decline Test", INVITEE);

        declineInvite(invitee, inviteId);

        assertThat(getPendingInvites(invitee)).isEmpty();
        assertThat(permissionsFacade.roleOf(RESOURCE_TYPE, resourceId, INVITEE)).isEmpty();
        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId))
                .extracting(PermissionDto::email)
                .doesNotContain(INVITEE);
    }

    @Test
    void shouldCancelPendingInviteWhenSharerUnshares() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        invite(resourceId, "Cancel Test", INVITEE);
        assertThat(getPendingInvites(invitee)).hasSize(1);

        permissionsFacade.revoke(RESOURCE_TYPE, resourceId, INVITEE, SHARER);

        assertThat(getPendingInvites(invitee)).isEmpty();
        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId))
                .extracting(PermissionDto::email)
                .doesNotContain(INVITEE);
    }

    @Test
    void shouldRefuseSecondInviteWhenOneIsAlreadyPending() {
        UUID resourceId = ownedResource();
        invite(resourceId, "Second Invite Test", INVITEE);

        assertThatExceptionOfType(InviteRefusedException.class)
                .isThrownBy(() -> invite(resourceId, "Second Invite Test", INVITEE))
                .extracting(InviteRefusedException::reason)
                .isEqualTo(InviteRefusedException.Reason.ALREADY_INVITED);
    }

    @Test
    void shouldRefuseInviteWhenTargetAlreadyHasAccess() {
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Already Has Access Test", INVITEE);
        acceptInvite(inviteeClient(), inviteId);

        assertThatExceptionOfType(InviteRefusedException.class)
                .isThrownBy(() -> invite(resourceId, "Already Has Access Test", INVITEE))
                .extracting(InviteRefusedException::reason)
                .isEqualTo(InviteRefusedException.Reason.ALREADY_HAS_ACCESS);
    }

    @Test
    void shouldRefuseInviteToTheResourceOwner() {
        UUID resourceId = ownedResource();

        assertThatExceptionOfType(InviteRefusedException.class)
                .isThrownBy(() -> invite(resourceId, "Invite Owner Test", SHARER))
                .extracting(InviteRefusedException::reason)
                .isEqualTo(InviteRefusedException.Reason.ALREADY_HAS_ACCESS);
    }

    @Test
    void shouldRejectInviteAtOwnerRole() {
        UUID resourceId = ownedResource();

        assertThatExceptionOfType(InvalidInviteRoleException.class)
                .isThrownBy(() -> permissionsFacade.invite(RESOURCE_TYPE, resourceId, INVITEE,
                        ResourceRole.OWNER, "Invite At Owner Role Test", SHARER));

        assertThat(getPendingInvites(inviteeClient())).isEmpty();
    }

    @Test
    void shouldReturn404WhenAcceptingAnInviteBelongingToSomeoneElse() {
        RestClient invitee = inviteeClient();
        RestClient stranger = restClient(TestSecurityConfiguration.AUTH_TOKEN);
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Wrong Owner Accept Test", INVITEE);

        try {
            acceptInvite(stranger, inviteId);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }

        // Invite still stands for the invitee
        assertThat(getPendingInvites(invitee)).extracting(PendingInviteDto::id).containsExactly(inviteId);
    }

    @Test
    void shouldReturn404WhenAcceptingAnUnknownInviteId() {
        RestClient invitee = inviteeClient();

        try {
            acceptInvite(invitee, UUID.randomUUID());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenDecliningAnAlreadyAnsweredInvite() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Already Answered Test", INVITEE);
        acceptInvite(invitee, inviteId);

        try {
            declineInvite(invitee, inviteId);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldRemovePendingInviteWhenResourceIsDeleted() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        invite(resourceId, "Delete Cascade Test", INVITEE);
        assertThat(getPendingInvites(invitee)).hasSize(1);

        permissionsFacade.resourceDeleted(RESOURCE_TYPE, resourceId);

        assertThat(getPendingInvites(invitee)).isEmpty();
    }

    @Test
    void shouldListInvitesOnlyForTheCallingEmail() {
        RestClient invitee = inviteeClient();
        RestClient stranger = restClient(TestSecurityConfiguration.AUTH_TOKEN);

        invite(ownedResource(), "For Invitee", INVITEE);
        invite(ownedResource(), "For Stranger", STRANGER);

        assertThat(getPendingInvites(invitee)).extracting(PendingInviteDto::label).containsExactly("For Invitee");
        assertThat(getPendingInvites(stranger)).extracting(PendingInviteDto::label).containsExactly("For Stranger");
    }

    @Test
    void shouldShowPendingAndGrantedTogetherInPermissions() {
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Mixed Permissions Test", INVITEE);
        acceptInvite(inviteeClient(), inviteId);
        invite(resourceId, "Mixed Permissions Test", STRANGER);

        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId)).containsExactly(
                new PermissionDto(SHARER, ResourceRole.OWNER, false),
                new PermissionDto(INVITEE, ResourceRole.EDITOR, false),
                new PermissionDto(STRANGER, ResourceRole.EDITOR, true)
        );
    }

    @Test
    void shouldListPendingInvitesAcrossDifferentResourceTypesInOneResponse() {
        UUID firstResourceId = ownedResource(RESOURCE_TYPE);
        UUID secondResourceId = ownedResource(OTHER_RESOURCE_TYPE);

        invite(RESOURCE_TYPE, firstResourceId, "First Type Test", INVITEE);
        invite(OTHER_RESOURCE_TYPE, secondResourceId, "Second Type Test", INVITEE);

        List<PendingInviteDto> invites = getPendingInvites(inviteeClient());

        assertThat(invites)
                .extracting(PendingInviteDto::resourceType, PendingInviteDto::label, PendingInviteDto::invitedBy)
                .containsExactlyInAnyOrder(
                        tuple(RESOURCE_TYPE, "First Type Test", SHARER),
                        tuple(OTHER_RESOURCE_TYPE, "Second Type Test", SHARER)
                );
    }
}

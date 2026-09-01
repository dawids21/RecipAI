package xyz.stasiak.recipai.permissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.TestIdentities;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.exception.InvalidInviteRoleException;
import xyz.stasiak.recipai.permissions.exception.InviteRefusedException;
import xyz.stasiak.recipai.permissions.exception.ResourceAccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

@IntegrationTest
class InviteIntegrationTest {

    // Opaque keys no module owns: the module holds no domain knowledge and never inspects them.
    // Two distinct types so a test can prove /invites mixes resource types in one response without
    // reaching into another module's resource type.
    private static final String RESOURCE_TYPE = "INVITE_TEST_RESOURCE";
    private static final String OTHER_RESOURCE_TYPE = "INVITE_TEST_RESOURCE_OTHER";

    private String sharerToken;
    private String inviteeToken;
    private String strangerToken;

    private String sharerEmail;
    private String inviteeEmail;
    private String strangerEmail;

    @LocalServerPort
    private int port;

    @Autowired
    private PermissionsFacade permissionsFacade;

    @BeforeEach
    void freshUsers() {
        sharerToken = TestIdentities.freshToken();
        inviteeToken = TestIdentities.freshToken();
        strangerToken = TestIdentities.freshToken();
        sharerEmail = TestIdentities.emailOf(sharerToken);
        inviteeEmail = TestIdentities.emailOf(inviteeToken);
        strangerEmail = TestIdentities.emailOf(strangerToken);
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
    }

    private RestClient inviteeClient() {
        return restClient(inviteeToken);
    }

    private UUID ownedResource() {
        return ownedResource(RESOURCE_TYPE);
    }

    private UUID ownedResource(String resourceType) {
        UUID resourceId = UUID.randomUUID();
        permissionsFacade.grantOwner(resourceType, resourceId, sharerEmail);
        return resourceId;
    }

    private UUID invite(UUID resourceId, String label, String targetEmail) {
        return invite(RESOURCE_TYPE, resourceId, label, targetEmail);
    }

    private UUID invite(String resourceType, UUID resourceId, String label, String targetEmail) {
        return permissionsFacade.invite(resourceType, resourceId, targetEmail, ResourceRole.EDITOR, label, sharerEmail);
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

        invite(resourceId, "Pending Access Test", inviteeEmail);

        assertThat(permissionsFacade.roleOf(RESOURCE_TYPE, resourceId, inviteeEmail)).isEmpty();
        assertThat(permissionsFacade.accessibleResources(RESOURCE_TYPE, inviteeEmail)).doesNotContainKey(resourceId);
        assertThatExceptionOfType(ResourceAccessDeniedException.class)
                .isThrownBy(() -> permissionsFacade.requireEditor(RESOURCE_TYPE, resourceId, inviteeEmail));
    }

    @Test
    void shouldListPendingInviteWithLabelAndSender() {
        UUID resourceId = ownedResource();

        invite(resourceId, "My Groceries", inviteeEmail);

        PendingInviteDto invite = onlyPendingInviteFor(inviteeClient());
        assertThat(invite.resourceType()).isEqualTo(RESOURCE_TYPE);
        assertThat(invite.label()).isEqualTo("My Groceries");
        assertThat(invite.invitedBy()).isEqualTo(sharerEmail);
        assertThat(invite.role()).isEqualTo(ResourceRole.EDITOR);
    }

    @Test
    void shouldGrantAccessWhenInviteIsAccepted() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Accept Test", inviteeEmail);

        acceptInvite(invitee, inviteId);

        assertThat(permissionsFacade.roleOf(RESOURCE_TYPE, resourceId, inviteeEmail)).contains(ResourceRole.EDITOR);
        assertThat(permissionsFacade.accessibleResources(RESOURCE_TYPE, inviteeEmail))
                .containsEntry(resourceId, ResourceRole.EDITOR);
    }

    @Test
    void shouldRemoveInviteFromBothSidesWhenAccepted() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Remove From Both Sides Test", inviteeEmail);

        acceptInvite(invitee, inviteId);

        assertThat(getPendingInvites(invitee)).isEmpty();
        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId))
                .contains(new PermissionDto(inviteeEmail, ResourceRole.EDITOR, false));
    }

    @Test
    void shouldLeaveResourceInvisibleWhenInviteIsDeclined() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Decline Test", inviteeEmail);

        declineInvite(invitee, inviteId);

        assertThat(getPendingInvites(invitee)).isEmpty();
        assertThat(permissionsFacade.roleOf(RESOURCE_TYPE, resourceId, inviteeEmail)).isEmpty();
        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId))
                .extracting(PermissionDto::email)
                .doesNotContain(inviteeEmail);
    }

    @Test
    void shouldCancelPendingInviteWhenSharerUnshares() {
        RestClient invitee = inviteeClient();
        UUID resourceId = ownedResource();
        invite(resourceId, "Cancel Test", inviteeEmail);
        assertThat(getPendingInvites(invitee)).hasSize(1);

        permissionsFacade.revoke(RESOURCE_TYPE, resourceId, inviteeEmail, sharerEmail);

        assertThat(getPendingInvites(invitee)).isEmpty();
        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId))
                .extracting(PermissionDto::email)
                .doesNotContain(inviteeEmail);
    }

    @Test
    void shouldRefuseSecondInviteWhenOneIsAlreadyPending() {
        UUID resourceId = ownedResource();
        invite(resourceId, "Second Invite Test", inviteeEmail);

        assertThatExceptionOfType(InviteRefusedException.class)
                .isThrownBy(() -> invite(resourceId, "Second Invite Test", inviteeEmail))
                .extracting(InviteRefusedException::reason)
                .isEqualTo(InviteRefusedException.Reason.ALREADY_INVITED);
    }

    @Test
    void shouldRefuseInviteWhenTargetAlreadyHasAccess() {
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Already Has Access Test", inviteeEmail);
        acceptInvite(inviteeClient(), inviteId);

        assertThatExceptionOfType(InviteRefusedException.class)
                .isThrownBy(() -> invite(resourceId, "Already Has Access Test", inviteeEmail))
                .extracting(InviteRefusedException::reason)
                .isEqualTo(InviteRefusedException.Reason.ALREADY_HAS_ACCESS);
    }

    @Test
    void shouldRefuseInviteToTheResourceOwner() {
        UUID resourceId = ownedResource();

        assertThatExceptionOfType(InviteRefusedException.class)
                .isThrownBy(() -> invite(resourceId, "Invite Owner Test", sharerEmail))
                .extracting(InviteRefusedException::reason)
                .isEqualTo(InviteRefusedException.Reason.ALREADY_HAS_ACCESS);
    }

    @Test
    void shouldRejectInviteAtOwnerRole() {
        UUID resourceId = ownedResource();

        assertThatExceptionOfType(InvalidInviteRoleException.class)
                .isThrownBy(() -> permissionsFacade.invite(RESOURCE_TYPE, resourceId, inviteeEmail,
                        ResourceRole.OWNER, "Invite At Owner Role Test", sharerEmail));

        assertThat(getPendingInvites(inviteeClient())).isEmpty();
    }

    @Test
    void shouldReturn404WhenAcceptingAnInviteBelongingToSomeoneElse() {
        RestClient invitee = inviteeClient();
        RestClient stranger = restClient(strangerToken);
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Wrong Owner Accept Test", inviteeEmail);

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
        UUID inviteId = invite(resourceId, "Already Answered Test", inviteeEmail);
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
        invite(resourceId, "Delete Cascade Test", inviteeEmail);
        assertThat(getPendingInvites(invitee)).hasSize(1);

        permissionsFacade.resourceDeleted(RESOURCE_TYPE, resourceId);

        assertThat(getPendingInvites(invitee)).isEmpty();
    }

    @Test
    void shouldListInvitesOnlyForTheCallingEmail() {
        RestClient invitee = inviteeClient();
        RestClient stranger = restClient(strangerToken);

        invite(ownedResource(), "For Invitee", inviteeEmail);
        invite(ownedResource(), "For Stranger", strangerEmail);

        assertThat(getPendingInvites(invitee)).extracting(PendingInviteDto::label).containsExactly("For Invitee");
        assertThat(getPendingInvites(stranger)).extracting(PendingInviteDto::label).containsExactly("For Stranger");
    }

    @Test
    void shouldShowPendingAndGrantedTogetherInPermissions() {
        UUID resourceId = ownedResource();
        UUID inviteId = invite(resourceId, "Mixed Permissions Test", inviteeEmail);
        acceptInvite(inviteeClient(), inviteId);
        invite(resourceId, "Mixed Permissions Test", strangerEmail);

        assertThat(permissionsFacade.getPermissions(RESOURCE_TYPE, resourceId)).containsExactly(
                new PermissionDto(sharerEmail, ResourceRole.OWNER, false),
                new PermissionDto(inviteeEmail, ResourceRole.EDITOR, false),
                new PermissionDto(strangerEmail, ResourceRole.EDITOR, true)
        );
    }

    @Test
    void shouldListPendingInvitesAcrossDifferentResourceTypesInOneResponse() {
        UUID firstResourceId = ownedResource(RESOURCE_TYPE);
        UUID secondResourceId = ownedResource(OTHER_RESOURCE_TYPE);

        invite(RESOURCE_TYPE, firstResourceId, "First Type Test", inviteeEmail);
        invite(OTHER_RESOURCE_TYPE, secondResourceId, "Second Type Test", inviteeEmail);

        List<PendingInviteDto> invites = getPendingInvites(inviteeClient());

        assertThat(invites)
                .extracting(PendingInviteDto::resourceType, PendingInviteDto::label, PendingInviteDto::invitedBy)
                .containsExactlyInAnyOrder(
                        tuple(RESOURCE_TYPE, "First Type Test", sharerEmail),
                        tuple(OTHER_RESOURCE_TYPE, "Second Type Test", sharerEmail)
                );
    }
}

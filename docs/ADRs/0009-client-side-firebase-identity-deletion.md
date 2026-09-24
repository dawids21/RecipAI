# ADR-0009: The Firebase identity is deleted by the mobile client, never by the backend

**Date:** 2026-09-04
**Status:** accepted
**Related ADRs:** [ADR-0011: Administrators are recognised by a configured email allowlist](0011-admin-authority-from-config-allowlist.md)

## Context

Deleting a RecipAI account has to remove the user's Firebase Auth identity as well as their data. Something
has to make that call, and there are only two candidates: the mobile app, which is already signed in as that
identity, or the backend.

The backend's relationship with Firebase is one-directional today. It validates Firebase-issued ID tokens
locally against Google's published keys, discovered from a configured issuer, and makes no outbound call to
Firebase at all. Deleting an identity from the server would change that. It needs a Google service account
with permission to look accounts up by email and delete them, its long-lived JSON private key placed on the
VPS and named by an environment variable, and either the Firebase Admin SDK — which brings Firestore, Cloud
Storage, gRPC and Netty into the build to issue two HTTPS requests — or a hand-rolled client over the Identity
Toolkit REST API, which needs no new dependency but means owning the request shapes and the emulator switch by
hand. It also introduces a question the backend does not have to answer today: whether a missing credential
stops the application booting or only fails the deletion call.

The mobile app needs none of that. The Firebase client SDK it already depends on can delete the signed-in
user's own identity, subject to one condition: the account must have signed in recently. The user-facing flow
re-authenticates with Google before deleting anyway, because deletion is irreversible and confirming intent
matters, so that condition costs nothing extra.

The two paths are not equivalent in reach. The client can only ever delete *its own* identity. An
administrator deleting someone else's account on an emailed request has no client-side option, so choosing the
client means the administrator's identity deletion becomes a manual step in the Firebase console. Deletion
requests of that kind arrive rarely and are already handled by hand end to end.

## Decision

The mobile client deletes the Firebase identity, using the client SDK, after the re-authentication its
deletion flow already performs. The backend never calls Firebase and gains no Firebase dependency, service
account or credential.

The self-service flow purges the backend first and deletes the identity second. That ordering is not
arbitrary: because the backend is keyed by email and holds no user records of its own, deleting the identity
first and then failing to purge would let the same person re-register with the same Google address and land
back on top of all their old data. Backend-first can only fail towards "data gone, identity alive", which
resolves to a fresh empty account on the next sign-in.

The admin deletion endpoint removes data only. The operator deletes the identity in the Firebase console.

## Alternatives considered

- **Firebase Admin SDK on the backend** — the documented path, and the only one with a first-class emulator
  story for tests, but it brings Firestore, Cloud Storage, gRPC, Netty and an HTTP client into the build for
  two HTTPS calls, and it still needs the service-account key.
- **Identity Toolkit REST API from the backend** — avoids the dependency weight entirely, since the OAuth token
  library is already on the compile classpath, but still needs the service-account key on the VPS and means
  owning the request and response shapes and the emulator base-URL switch by hand.
- **Client for self-service, backend for admin** — would give the admin path a complete end state, but it
  reintroduces the credential, the dependency choice and the boot-time question for the sake of one rarely used
  endpoint, which is the entire cost the decision was avoiding.

## Consequences

The backend keeps its current relationship with Firebase: a token issuer it validates against and never talks
to. No long-lived Google credential exists on the VPS, nothing new has to be injected into the container, and
the integration suite — which authenticates through synthetic dev-profile callers that exist in no Firebase
project — needs no emulator container and no test double for an identity client.

Re-authentication becomes structurally required rather than a product choice, since the client SDK will not
delete an identity without a recent sign-in.

The admin endpoint does not produce a complete end state on its own. Between the endpoint call and the
operator's console action, the deleted person can still sign in — and gets a fresh, empty account, because
their data is already gone. If deletion requests ever arrive in volume, the manual step is what fails to
scale, and this decision is the one to revisit.

Because the client and the backend delete in sequence with no transaction between them, a failure after the
purge leaves an orphaned identity. That is the same partial-deletion posture the system already takes towards
S3 objects left behind by a failed image cleanup.

# Talking to Firebase from the backend to delete an identity

## Summary

Deleting a Firebase identity from the server is a two-call operation — look the account up by email to get its
`localId`/uid, then delete it by uid — and Google exposes it both through the Firebase Admin SDK and through the
raw Identity Toolkit REST API. Everything else is credential plumbing: a Google service account with
`firebaseauth.users.get` and `firebaseauth.users.delete` on project `recipai-751ae`, its JSON private key placed
on the VPS and named by an environment variable, and a bean that reads it.

The choice worth making deliberately is SDK versus REST. `com.google.firebase:firebase-admin` is the documented,
obvious path but drags in `google-cloud-firestore`, `google-cloud-storage`, gRPC, Netty and Apache HttpClient 5
for what is ultimately two HTTPS calls. The REST path needs **no new dependency at all**: the OAuth token minter
(`google-auth-library-oauth2-http`) is already on this project's compile classpath, and Spring's `RestClient` can
make the calls.

The re-authentication and token-validity questions this raises are already answered in
[`admin-role-concept.md`](admin-role-concept.md) and are not repeated here.

## Key findings

- **The delete needs a uid, not an email.** Both APIs delete by `localId`/uid, so an email-keyed backend must
  look the account up first. Lookup matches the **primary** email only — a linked provider address does not
  resolve.
- **Two permissions, one role.** `firebaseauth.users.get` (lookup) and `firebaseauth.users.delete` (delete) on
  the target project. `roles/firebaseauth.admin` contains both; so does `roles/firebase.sdkAdminServiceAgent`,
  which the auto-created `firebase-adminsdk-*@recipai-751ae.iam.gserviceaccount.com` account already holds — the
  key generated from the Firebase console therefore works with no IAM change.
- **The credential is a service account JSON key.** On a non-Google host there is no metadata server, so
  Application Default Credentials resolve from `GOOGLE_APPLICATION_CREDENTIALS` pointing at that file, or the
  file is read explicitly with `GoogleCredentials.fromStream(...)`.
- **`firebase-admin` is 9.10.0** (2 July 2026) and is a heavy dependency for this use — see the footprint below.
- **The REST path adds zero dependencies.** `com.google.auth:google-auth-library-oauth2-http:1.33.0` is already
  resolved at `compile` scope, pulled in by Spring AI's `google-genai`.
- **`deleteUser` on a missing account is an error, not a no-op** — `auth/user-not-found`. A deletion flow that
  must be idempotent has to swallow it deliberately.
- **Tests have an emulator.** The Admin SDK redirects to the Firebase Auth emulator when
  `FIREBASE_AUTH_EMULATOR_HOST` is set (host:port, no `http://`). A hand-rolled REST client gets no such switch
  for free.

## Details

### The API surface

Admin SDK (Java):

```java
UserRecord user = FirebaseAuth.getInstance().getUserByEmail(email);
FirebaseAuth.getInstance().deleteUser(user.getUid());
```

`deleteUsersAsync(List<String> uids)` exists for batches and reports per-entry failures through
`DeleteUsersResult`, but a single-account deletion has no use for it.

The same thing over REST, both `POST` to `https://identitytoolkit.googleapis.com/v1/`, authorised by a bearer
OAuth 2.0 access token (scope `https://www.googleapis.com/auth/identitytoolkit` or `.../cloud-platform`):

| Call | Body | IAM permission |
|---|---|---|
| `accounts:lookup` | `{"email": ["<email>"], "targetProjectId": "recipai-751ae"}` | `firebaseauth.users.get` |
| `accounts:delete` | `{"localId": "<uid>", "targetProjectId": "recipai-751ae"}` | `firebaseauth.users.delete` |

`accounts:lookup` returns `GetAccountInfoResponse`, whose `users[0].localId` is the uid the delete needs. The
API-key form of these endpoints (`?key=…`) is the *client* form and takes an end-user `idToken` instead; the
service-account form uses `localId` + `targetProjectId` and no API key.

Relevant error codes: `auth/user-not-found`, `auth/invalid-email`, `auth/insufficient-permission` (the
credential lacks the IAM permission), `auth/internal-error`.

### Admin SDK vs REST

`firebase-admin:9.10.0` declares, besides the expected `google-auth-library-oauth2-http` and `google-api-client`:
`google-cloud-firestore`, `google-cloud-storage`, `guava`, three Netty modules, `httpclient5` and
`nimbus-jose-jwt`, with versions driven by an imported `com.google.cloud:libraries-bom:26.83.0`. None of the
Firestore or Storage surface is wanted here.

Two collisions to check rather than assume, because this project already carries a Google stack from Spring AI:

- **Guava flavour.** The tree currently resolves `guava:33.4.0-android` (via `google-genai`); `libraries-bom`
  supplies the `-jre` flavour. Both arrive at the same depth, so Maven's nearest-wins tie-break decides by
  declaration order — worth confirming with `./mvnw dependency:tree` after adding the dependency.
- **`nimbus-jose-jwt`.** Currently `10.9`, managed by the Spring Boot parent for `spring-security-oauth2-jose`;
  `firebase-admin` pins `10.9.1` itself. Spring Boot's `dependencyManagement` overrides transitive versions, so
  `10.9` should hold, but the SDK is then running against a version it did not pin.

The REST alternative avoids all of it. `GoogleCredentials.fromStream(...).createScoped(...)` produces the access
token; `RestClient` (already available through `spring-boot-starter-webmvc`) makes the two calls. The cost is
owning two request/response record pairs and the emulator base-URL switch by hand, and depending on
`google-auth-library-oauth2-http` explicitly rather than inheriting it from Spring AI.

### Provisioning the credential

1. Firebase console → **Project settings → Service accounts → Generate new private key** → a JSON file for
   `firebase-adminsdk-*@recipai-751ae.iam.gserviceaccount.com`, which already carries
   `roles/firebase.sdkAdminServiceAgent`.
2. Tighter alternative: create a dedicated service account in the Google Cloud console, grant it only
   `roles/firebaseauth.admin`, and generate a key for that. This keeps the deletion credential from also being
   able to write Realtime Database rules and Security Rules, which the SDK service agent role permits.
3. `identitytoolkit.googleapis.com` is the API behind both paths; it is already serving this project, since
   Firebase Authentication itself runs on it.

The key is a long-lived static credential — the thing Google's own setup guide singles out as needing "extremely
high security awareness". It never belongs in the repository or in the Docker image.

### Getting it onto the VPS

The image is built by GitHub Actions and run as a container; every other secret is an environment variable
(`SPRING_DATASOURCE_PASSWORD`, `SPRING_AI_API_KEY`, `AWS_*`), per
`docs/backend/standards/configuration-profiles.md`. Two shapes fit that:

- **Mounted file.** Place the JSON outside the image, bind-mount it read-only, set
  `GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase.json`. This is the path Google documents and the one the
  SDK picks up with no code. It needs the file to be readable by the container's non-root `recipai` user, which
  the Dockerfile creates.
- **Base64 environment variable.** Hold the whole JSON in one variable, decode it into a `ByteArrayInputStream`
  and pass it to `GoogleCredentials.fromStream(...)`. Nothing new on the filesystem and it matches the existing
  all-env-vars deployment, at the price of a large opaque variable and explicit code.

Either way the variable joins the production table in `docs/project/architecture.md`.

### Wiring it into the backend

`config.security` is where the requirements place the caller identity, but the shape to copy is `config.s3`: a
public seam interface (`S3Service`) with a package-private implementation holding the vendor types, and a
dedicated exception (`S3StorageException`) — no AWS type in any signature, which is what makes it substitutable
in tests. A Firebase identity seam wants the same: one method taking an email, a `@ConfigurationProperties`
record for the project id and credential location, and a `@Configuration` producing the client bean.

Note that `S3Config` builds its credential provider eagerly but resolves lazily, so the app boots without AWS
credentials. `FirebaseApp.initializeApp` reads the credential at startup instead, so a naive port makes a missing
key file a boot failure rather than a call-time failure — worth deciding on purpose.

### Dev and test

The `dev` profile deliberately has no real identity: `DevAuthConfig` mints callers like `alice@local.test`, which
exist in no Firebase project. Two consequences:

- A dev-profile no-op implementation of the seam (log and return) keeps local runs and the existing integration
  suite — which activates `dev` alongside `test` — free of Firebase credentials entirely. This is the cheapest
  option and mirrors how the suite already avoids real AWS.
- If the deletion path itself needs coverage against a real Firebase surface, the Firebase Auth emulator is the
  vehicle: `FIREBASE_AUTH_EMULATOR_HOST=host:port` (no scheme) redirects the Admin SDK, and the community
  `testcontainers-firebase` container starts the emulator suite, with Auth among its verified emulators. That is
  a new container in a suite the team has recently been trimming for runtime, so it is a real cost.

### Failure behaviour

`FirebaseAuthException` is checked in the Java SDK, so the seam has to translate it. The requirements accept an
orphaned Firebase identity when the database rows are already gone ("partial deletion"), which means the call
sequence and the transaction boundary matter more than retries: an outbound HTTPS call inside a database
transaction holds the connection for the round trip, and a failure after commit is the accepted-orphan case.

## Recommendation

Take the **REST path** unless the Admin SDK is wanted for something else. The deciding factor is that
`admin-role-concept.md` recommends a config allowlist over Firebase custom claims for the admin role — and custom
claims were the other reason to carry the SDK. Without them, `firebase-admin` brings Firestore, Storage, gRPC and
Netty into the build to issue two HTTPS requests that the already-present auth library and `RestClient` can make
between them.

Concretely: a dedicated service account with `roles/firebaseauth.admin`, its key mounted as a file and named by
`GOOGLE_APPLICATION_CREDENTIALS`, a seam interface in `config.security` modelled on `S3Service`, and a
dev-profile no-op so the existing test suite needs no credential and no new container.

If the SDK is chosen anyway, the tie-breaker in its favour is the emulator: it is the only way to test the real
call without hand-writing a base-URL switch.

## Open questions / gaps

- Whether the deletion should be verified end-to-end at all, or trusted the way the S3 seam is. This decides
  whether the emulator and its container are needed, and that in turn is the strongest argument for the SDK.
- Where the seam lives. `config.security` is named in the requirements, but that package currently holds only
  inbound token validation; an outbound Firebase client may deserve its own `config.firebase`.
- Whether the credential resolves at startup (fail fast, no boot without a key) or at first call (boots, fails on
  deletion) — the two paths differ here by default.
- Not investigated: whether the VPS deployment is compose-driven or plain `docker run`, which decides how a
  mounted key file is actually wired.
- Not investigated: Workload Identity Federation as a way to avoid a long-lived key. It is the standard answer
  for non-Google hosts but needs an external identity provider the VPS can present, which this deployment does
  not obviously have.

## Sources

- [Add the Firebase Admin SDK to your server](https://firebase.google.com/docs/admin/setup) — Maven coordinates
  and version 9.10.0, the three initialisation forms, generating a private key from the console, and the
  security warning about service account keys.
- [Firebase Admin Java SDK release notes](https://firebase.google.com/support/release-notes/admin/java) — 9.10.0
  released 2 July 2026; `libraries-bom` upgrades.
- [`firebase-admin-9.10.0.pom`](https://repo1.maven.org/maven2/com/google/firebase/firebase-admin/9.10.0/firebase-admin-9.10.0.pom)
  — the declared dependency set (Firestore, Storage, Netty, httpclient5, nimbus-jose-jwt 10.9.1) and the
  `libraries-bom:26.83.0` import.
- [Managing users programmatically](https://docs.cloud.google.com/identity-platform/docs/admin/manage-users) —
  Java samples for `getUserByEmail`, `deleteUser`, `deleteUsersAsync`, and the primary-email-only lookup rule.
- [Admin Authentication API Errors](https://firebase.google.com/docs/auth/admin/errors) — `auth/user-not-found`,
  `auth/invalid-email`, `auth/insufficient-permission`, `auth/internal-error`.
- [Method: accounts.delete](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/delete)
  — the `localId` + `targetProjectId` admin form, OAuth scopes, and the `firebaseauth.users.delete` permission.
- [Method: accounts.lookup](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/lookup)
  — admin lookup by `email[]`, and the `firebaseauth.users.get` permission.
- [Firebase Authentication roles and permissions](https://docs.cloud.google.com/iam/docs/roles-permissions/firebaseauth)
  — the contents of `roles/firebaseauth.admin`, and confirmation that `roles/firebase.sdkAdminServiceAgent`
  includes `firebaseauth.users.delete` and `firebaseauth.users.get`.
- [Identity Platform access control](https://docs.cloud.google.com/identity-platform/docs/access-control) —
  per-method permission table (DeleteAccount, GetAccountInfo).
- [Connect your app to the Authentication Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth)
  — `FIREBASE_AUTH_EMULATOR_HOST` and the no-scheme requirement.
- [testcontainers-firebase](https://github.com/alfa1-group/testcontainers-firebase) — a Testcontainers container
  for the Firebase emulator suite, with Auth among the verified emulators.
- Local: `./mvnw dependency:tree` on `backend/` (2026-09-04) — `google-auth-library-oauth2-http:1.33.0`,
  `google-http-client:1.46.2`, `guava:33.4.0-android` and `nimbus-jose-jwt:10.9` already resolved at compile
  scope; `backend/pom.xml`, `backend/Dockerfile`, `application*.yml`, `S3Config`, `DevAuthConfig`.

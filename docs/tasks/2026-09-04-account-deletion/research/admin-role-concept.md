# Admin / role concept in the backend

## Summary

Firebase can carry an admin flag, via **custom claims** — arbitrary key/values the Admin SDK writes onto a
user account, which Firebase then embeds in every ID token that account mints. Because the backend already
validates Firebase-issued JWTs, such a claim would arrive for free and Spring Security can turn it into a
`ROLE_ADMIN` authority with a converter. The cost is that claims are only settable from code (no console UI),
go stale for up to an hour after a change, and require the Firebase Admin SDK and a service account on the
VPS.

The lighter alternative is to keep the decision in the backend: a configured list of admin emails, checked
against the `email` claim the controllers already read. For a single operator handling deletion requests by
email, this is the smaller change and needs no new infrastructure.

Separately, one finding cuts against a stated constraint: deleting an identity through the Admin SDK does
**not** require a recent sign-in. That requirement belongs to the client SDK's `user.delete()`. See
[Effect on the deletion requirements](#effect-on-the-deletion-requirements).

## Key findings

### What exists in the backend today

- `config/security/SecurityConfig.java` is a single filter chain: `/actuator/health` is `permitAll`, the known
  API prefixes are `.authenticated()`, and `anyRequest().denyAll()`. There is no authority check anywhere, and
  method security (`@EnableMethodSecurity`) is not switched on.
- `application.yml` sets only `spring.security.oauth2.resourceserver.jwt.issuer-uri:
  https://securetoken.google.com/recipai-751ae`. Spring discovers Google's public keys from that issuer and
  validates signature, issuer, audience and expiry locally. The backend makes no outbound call to Firebase.
- Every controller derives identity the same way — `jwt.getClaimAsString("email")` (e.g.
  `LimitsController.java:22`). The `Authentication` object's authorities are unused.
- Spring's default authority extraction reads the `scope`/`scp` claim and prefixes `SCOPE_`. Firebase ID
  tokens carry no `scope` claim, so **the authority collection is empty on every request today**. Any
  role scheme has to populate it (or bypass it).
- `config/security/DevAuthConfig.java` (dev profile) mints a `Jwt` locally: bearer token `alice` becomes
  `alice@local.test`, with claims `sub`, `email`, `email_verified`. The integration-test suite runs
  `@ActiveProfiles({"dev","test"})` and authenticates through this decoder, so **whatever grants admin has to
  be expressible in the dev profile too**, or admin endpoints become untestable.
- `pom.xml` has no Firebase dependency. Adding one is new ground for the backend.

### Option A — Firebase custom claims

How it works:

- A privileged server sets claims on a user: `FirebaseAuth.getInstance().setCustomUserClaims(uid,
  Map.of("admin", true))`. The payload is capped at **1000 bytes** and each call **overwrites all existing
  custom claims** for that user.
- Firebase merges the claims into the ID token. They reach the backend inside the JWT it already validates —
  no extra call, no extra latency.
- Spring maps them to authorities via `JwtAuthenticationConverter`. Note the built-in
  `JwtGrantedAuthoritiesConverter` reads a claim that is a space-delimited **string** or a **collection**, so a
  boolean `admin: true` does not fit it: either store `roles: ["admin"]` and configure
  `setAuthoritiesClaimName("roles")` + `setAuthorityPrefix("ROLE_")`, or write a small
  `Converter<Jwt, AbstractAuthenticationToken>` that reads the boolean. Enforcement is then either
  `.requestMatchers("/admin/**").hasRole("ADMIN")` in the existing chain, or `@PreAuthorize("hasRole('ADMIN')")`
  with `@EnableMethodSecurity` (`hasRole('ADMIN')` looks for the authority `ROLE_ADMIN`).

Costs and caveats:

- **Grant/revoke is not immediate.** A changed claim appears only when the ID token is re-issued — on
  sign-in, on hourly expiry, or on a client-forced `getIdToken(true)`. Revoking admin leaves the old token
  admin-capable for up to an hour unless the refresh token is revoked (`revokeRefreshTokens(uid)`) *and* the
  backend verifies with `verifyIdToken(idToken, true)`, which Firebase's own docs call an expensive extra
  network round trip per request. That check is not something the current `issuer-uri` setup does at all.
- **No console UI.** Claims cannot be viewed or edited in the Firebase console; granting admin means running
  Admin SDK code or a script.
- **New dependency and credential.** `com.google.firebase:firebase-admin` (9.10.0 at time of writing) plus a
  service account. On a non-Google host, credentials come from a service account JSON — either
  `GOOGLE_APPLICATION_CREDENTIALS` pointing at the file with `GoogleCredentials.getApplicationDefault()`, or
  `GoogleCredentials.fromStream(...)`. Per `docs/backend/standards/configuration-profiles.md`, that path/secret
  is an environment variable, never `application.yml`.
- **Dev profile.** `DevAuthConfig` would need to synthesise the claim for designated dev callers (e.g.
  `admin@local.test` gets `roles: ["admin"]`) so integration tests can exercise the admin endpoint.

### Option B — backend-owned admin allowlist

Configure the admin emails (`recipai.security.admin-emails`, injected from an environment variable in prod)
and compare against the `email` claim the controllers already read. Grant `ROLE_ADMIN` in the same
`JwtAuthenticationConverter` seam Option A would use, so enforcement and endpoint shape are identical — only
the source of truth differs.

- No new dependency, no service account, no propagation delay: revoking admin takes effect on the next request
  after a restart.
- Firebase stays what it is today, a token issuer the backend never calls (except for the identity deletion the
  task already requires).
- Works unchanged in the dev profile, since `alice@local.test` is just another email to list in
  `application-dev.yml` / `application-test.yml`.
- Changing the admin set requires a config change and restart. For the operational driver in the requirements —
  one person handling emailed deletion requests — that is not a real constraint.

### Option C — database-backed roles

An `admin_user` table (or a role column on a users table) is the most flexible and the only option that
supports changing admins at runtime without a restart or a token refresh. It costs a Flyway migration, a
repository, and a lookup per authenticated request. The requirements note there is no users table today and
identity is email-keyed throughout; introducing one for a single flag is disproportionate to the driver.

### Comparison

| | Firebase custom claims | Config allowlist | DB table |
|---|---|---|---|
| New dependency / infra | firebase-admin + service account | none | migration + repository |
| Grant takes effect | after token refresh (≤1h) | after restart | immediately |
| Revoke takes effect | ≤1h, or per-request revocation check | after restart | immediately |
| Per-request cost | none | none | one query (cacheable) |
| Managing admins | Admin SDK script; no UI | edit env var, restart | SQL / endpoint |
| Dev-profile story | synthesise the claim in `DevAuthConfig` | list a `@local.test` email | seed a row |

Enforcement is the same in all three: an authority on the `Authentication`, checked in `SecurityConfig` or via
`@PreAuthorize`. That makes the choice reversible — switching later changes where the authority comes from,
not the endpoint or its tests.

### Effect on the deletion requirements

- **The Admin SDK is arriving regardless.** Both endpoints must delete a Firebase identity, which means
  `FirebaseAuth.getUserByEmail(email)` then `deleteUser(uid)` from the backend. If that dependency lands
  anyway, the marginal cost of Option A drops to the service-account credential and the propagation caveats.
- **Re-authentication is a product choice, not a Firebase constraint.** The requirements state that deleting a
  Firebase identity requires a recent sign-in. That holds for the *client* SDK's `user.delete()`; the Admin SDK
  deletes users with elevated privileges and no re-authentication requirement. Since the backend performs the
  deletion in both flows, the Google re-auth step is justified by intent-confirmation for an irreversible
  action, not by a Firebase precondition. Worth settling explicitly in design.
- **A deleted user's token stays valid.** The backend validates JWTs locally against Google's public keys, so
  after the identity is gone an already-issued ID token still passes validation until it expires (up to an
  hour). This is the same mechanism behind the "second device still signed in" edge case — and the requirements
  already accept it. Closing it would mean the per-request `checkRevoked` round trip.
- **Admin endpoint shape.** With an authority in place, the admin path is a separate matcher in the existing
  chain (`.requestMatchers("/admin/**").hasRole("ADMIN")`), which keeps the `anyRequest().denyAll()` default
  intact and leaves the admin route invisible to non-admin callers as a 403.

## Recommendation

Start with **Option B**, the config allowlist, and put the grant behind a `JwtAuthenticationConverter` so the
source of truth is one bean. It answers the open question ("how is an email recognised as having admin
rights?") with the smallest change, needs nothing from Firebase beyond what the deletion work already forces,
and stays testable in the dev profile. Move to custom claims if a second administrator appears or if admin
rights ever need to change without a restart — the endpoint, the authority and its tests survive that move
unchanged.

## Open questions / gaps

- Does the admin endpoint want more than an authority — a re-auth, a second factor, an audit line? The
  requirements ask this and mark it deferred; none of the three options answers it, and the anti-requirements
  rule out retaining an audit trail.
- If custom claims are chosen, who runs the one-off script that sets them, and where does it live — a
  throwaway, a Maven profile, an admin endpoint that bootstraps the first admin?
- Whether the service account credential is worth introducing at all is really a question about the Firebase
  identity deletion, not about roles. If that deletion were ever dropped or moved client-side, Option A loses
  its main justification.
- Not investigated: whether the deployment VPS can hold a service account JSON safely, and how it would be
  injected into the container.

## Sources

- [Control Access with Custom Claims and Security Rules](https://firebase.google.com/docs/auth/admin/custom-claims) — `setCustomUserClaims` Java usage, the 1000-byte cap, overwrite semantics, propagation on token refresh, and Firebase's own advice to prefer a database lookup for frequently-changing roles.
- [Manage Users — Firebase Admin SDK](https://firebase.google.com/docs/auth/admin/manage-users) — `getUserByEmail`, and the statement that the Admin SDK deletes users without the credential/recent-sign-in requirements that apply client-side.
- [Manage Session Lifetime](https://firebase.google.com/docs/auth/admin/manage-sessions) — one-hour ID token lifetime, `revokeRefreshTokens`, and the cost of `verifyIdToken(token, checkRevoked=true)`.
- [Add the Firebase Admin SDK to your server](https://firebase.google.com/docs/admin/setup) — `com.google.firebase:firebase-admin` Maven coordinates, `FirebaseOptions` initialisation, and application default vs. explicit service account credentials outside Google-hosted environments.
- [Spring Security — OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — default `scope`/`SCOPE_` extraction, `JwtGrantedAuthoritiesConverter` claim-name and prefix configuration, and registering a converter on the `oauth2ResourceServer` DSL.
- [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) — `@EnableMethodSecurity` is off by default in Spring Boot, and `hasRole('ADMIN')` resolves to the `ROLE_ADMIN` authority.
- [Baeldung — Mapping authorities from a JWT](https://www.baeldung.com/spring-security-map-authorities-jwt) — worked example of the converter pattern for a non-standard authorities claim.
- [Role-based authorization with Firebase Auth custom claims and Spring Security](https://medium.com/comsystoreply/role-based-authorization-rbac-with-firebase-auth-custom-claims-and-spring-security-6125c6fc7c4) — the specific Firebase + Spring resource server combination; listed for completeness, but the page returned HTTP 403 and its content is not reflected above.
- [How to Set Up Firebase Auth with Custom Claims for RBAC in GCP](https://oneuptime.com/blog/post/2026-02-17-how-to-set-up-firebase-auth-with-custom-claims-for-role-based-access-control-in-gcp/view) — corroborates the operational friction: no console UI for claims, and stale roles until token refresh.
- [Firebase: Revoking auth tokens with Admin SDK](https://hiranya911.medium.com/firebase-revoking-auth-tokens-with-admin-sdk-ac62c73bfdb0) — the ID-token vs. refresh-token split behind the revocation caveat.

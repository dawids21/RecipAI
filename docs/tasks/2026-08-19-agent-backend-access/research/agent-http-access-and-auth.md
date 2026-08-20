# Letting a coding agent run the backend and call it with curl

## Summary

Yes, and the running half already works today with no code changes — verified on this machine on
2026-08-19: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` with JDK 26 booted in 4.8 s, auto-started
its own PostgreSQL container, ran Flyway, and served `/actuator/health` (permitted anonymously) while
returning `401` on every business endpoint. Only authentication is missing.

The token problem is smaller than it looks, because **the backend identifies users by the `email`
claim, not by `sub`** — every controller calls `jwt.getClaimAsString("email")`. Nothing in the codebase
depends on a Firebase-issued `sub`, a Firebase uid, or any Google-specific claim. Any JWT carrying an
`email` claim that the configured `JwtDecoder` accepts is a fully valid caller.

That points to two complementary answers. For local agent work, add a dev-profile `JwtDecoder` that
mints a caller from a static token — the pattern already exists in `TestSecurityConfiguration` and it
removes Firebase, network, and expiry from the loop entirely. Enabling email+password in Firebase is
also worth doing, but as the *second* mechanism: it is the only option that also works against the
deployed instance, and it is genuinely simple (one console toggle, one `curl`), but it saddles the agent
with a 3600-second non-configurable token lifetime and a credential to store.

---

## Key findings

### Running the app

- **Verified working.** Full boot log in the appendix. Startup took 4.8 s.
- **JDK 26 is required** (`<java.version>26</java.version>` in `backend/pom.xml`). The shell default here
  is JDK 21, but `/usr/lib/jvm/java-26-openjdk` is installed — `JAVA_HOME` must be set explicitly or the
  build fails.
- **No database setup needed.** `application-dev.yml` deliberately defines no datasource; the optional
  runtime dependency `spring-boot-docker-compose` sees `backend/compose.yaml`, starts
  `backend-postgres-1` on a random host port, waits for health, and **stops it again on shutdown**.
  Docker 29.6.1 is present. Flyway then validated 21 migrations against schema `recipai`.
- **`SPRING_AI_API_KEY` must be set to *something*** or startup fails. `application.yml` has
  `api-key: ${SPRING_AI_API_KEY}` with no default, so an unresolvable placeholder aborts context
  creation. A dummy string is enough — it is only rejected at call time, by `/extract/**`.
- **AWS credentials are not needed to boot.** `S3Config` builds a `DefaultCredentialsProvider`, which
  resolves lazily; the `S3Client` and `S3Presigner` beans construct fine without credentials. Only the
  image endpoints fail without them.
- **Limits are already off in dev** (`recipai.limits.enabled: false`), confirmed by the startup log line
  `Usage limits are DISABLED`. An agent will not hit spurious `429`s.
- **`/actuator/**` is `permitAll`**, so `curl -s localhost:8080/actuator/health` is a clean readiness
  probe that needs no token. It returned `{"groups":["liveness","readiness"],"status":"UP"}`.
- **Everything else is `denyAll` or `authenticated`.** An unknown path returns `401`, not `404` — worth
  knowing, since an agent will otherwise misread a typo'd URL as an auth failure.

### The identity model — the decisive detail

Every controller in `recipes`, `recipes.collections`, `planning`, `shoppinglists` and `extraction`
resolves the caller the same way:

```java
String userEmail = jwt.getClaimAsString("email");
```

`sub` is never read in `src/main`. Ownership rows, permission tables and limit subjects are all keyed by
email string. Consequences:

- A synthetic token needs exactly one meaningful claim.
- A Firebase **email/password** account works identically to a Google account, because password-provider
  ID tokens carry `email` and `email_verified` just as Google ones do.
- Multi-user scenarios (sharing, permissions) need nothing more than a second distinct email.
- Unrelated but worth flagging: because identity *is* the email, a user changing their Google email
  address silently loses access to all their data. Out of scope here, but it is a latent bug.

### What the resource server actually validates

`SecurityConfig` uses `oauth2ResourceServer().jwt()` with
`issuer-uri: https://securetoken.google.com/recipai-751ae`. Spring resolves that issuer's OIDC discovery
document (confirmed live — it points at `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`,
RS256) and applies `JwtValidators.createDefaultWithIssuer`: **signature, issuer, and timestamps**.

There is no audience validator. Any correctly signed, unexpired ID token from Firebase project
`recipai-751ae` is accepted regardless of which app or provider minted it. This matters twice below.

---

## The options

### Option A — a dev-profile `JwtDecoder` (recommended for local agent work)

Register a `JwtDecoder` bean, active only under the `dev` profile, that returns a `Jwt` with a fixed
`email` claim instead of calling Firebase. This is the same trick `TestSecurityConfiguration` already
plays for integration tests, so the pattern is established and reviewed.

The worthwhile refinement is to treat **the bearer token string as the email**:

```
curl -H "Authorization: Bearer agent@local.test" localhost:8080/recipes
```

One bean, no fixture list, and multi-user permission testing comes free — `alice@local.test` and
`bob@local.test` are two callers with no extra setup.

- **Cost to agent per call:** a constant string. No expiry, no refresh, no network, no secret.
- **Works offline**, which matters if an agent is iterating in a loop.
- **Risk:** it is an authentication bypass living in the main source tree. Mitigations, in order of
  importance: the default active profile is `prod` (`application.yml`), so it is off unless explicitly
  requested; gate it on a dedicated property (e.g. `recipai.security.dev-auth.enabled`, default `false`)
  via `@ConditionalOnProperty` rather than on the profile alone, so enabling `dev` for an unrelated
  reason cannot switch it on; and log loudly at `WARN` on startup, the way `LimitsFacade` already does
  for its kill-switch. Per `docs/backend/standards/configuration-profiles.md` the flag belongs in
  `application-dev.yml`, never in `application.yml`.
- **Does not help** against the deployed backend — which is exactly what Option B is for.

### Option B — enable Firebase email+password (recommended as the second mechanism)

This is the option you asked about, and it does work. Steps:

1. Firebase console → Authentication → Sign-in method → enable the **Email/Password** provider.
2. Create a dedicated agent account (console "Add user", or the `accounts:signUp` REST endpoint).
   **Use a distinct address**, e.g. `agent@…`, not your own Google address: Firebase's default
   one-account-per-email behaviour will reject a password account on an email already held by the Google
   provider, and you do not want an agent mutating your real recipes.
3. Take the **Web API key** from Project settings → General. Do *not* reflexively reuse the key in
   `mobile/android/app/google-services.json` — Firebase auto-provisions Android keys with API
   restrictions applied, and a key carrying Android application restrictions expects
   `X-Android-Package` / `X-Android-Cert` headers on Identity Toolkit calls. The Web key avoids that
   class of failure.
4. Exchange credentials for a token:

```bash
curl -s 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=WEB_API_KEY' \
  -H 'Content-Type: application/json' \
  --data-binary '{"email":"agent@example.com","password":"…","returnSecureToken":true}'
```

The response carries `idToken`, `refreshToken` and `expiresIn`.

- **Token lifetime is 3600 s and is not configurable.** An agent working for longer than an hour must
  refresh, via `POST https://securetoken.googleapis.com/v1/token?key=WEB_API_KEY` with
  `grant_type=refresh_token&refresh_token=…` (form-encoded), or by simply re-running the sign-in call.
- **Works against the deployed backend**, unchanged. This is its unique advantage.
- **Requires storing a password and an API key** outside the repo — environment variables or a
  gitignored file.
- **Security consideration.** Enabling the provider means anyone holding the Web API key (which is
  client-distributed and therefore effectively public) can self-register an account and, given the
  missing audience check, call your production API as a valid user. That is *already* true via Google
  sign-in, so the model does not change in kind — but it makes scripted account creation trivial. See
  open questions on how to close it.

### Option C — Firebase Auth emulator (rejected)

The emulator issues **unsigned** ID tokens, accepted only by other emulators and by the Admin SDK when
`FIREBASE_AUTH_EMULATOR_HOST` is set. Spring's `NimbusJwtDecoder` verifies RS256 signatures against
Google's JWKS and will reject them outright. Making it work would require backend changes anyway — at
which point Option A is strictly simpler and has no emulator to install or run.

### Option D — custom tokens via an Admin SDK service account (not recommended)

Mint a custom JWT with a service account, exchange it at `accounts:signInWithCustomToken`. Two problems:
it puts a real production service-account private key on the dev machine, and the `email` claim this
codebase depends on only appears if the underlying user record actually has an email — so you end up
provisioning users anyway. Strictly more moving parts than Option B for no gain here.

---

## Recommendation

Do both A and B; they are not exclusive and they cover different situations.

- **A** is the everyday path: an agent iterating locally should not be making network round-trips to
  Google to read a recipe list.
- **B** is the escape hatch for verifying against real Firebase tokens and against the deployed VPS
  instance. It costs one console toggle and one account.

Whichever is used, **wrap the token in a script** so the agent never handles it directly — this is the
real fix for "pass it to every call":

```bash
curl -H "Authorization: Bearer $(scripts/dev-token.sh)" localhost:8080/recipes
```

Under A the script echoes a constant. Under B it caches `idToken`/`refreshToken` in a gitignored file
and silently refreshes when under ~5 minutes remain. The agent's call sites are then identical in both
modes, and switching between local and deployed is one environment variable.

The last mile is documentation: a short "running the backend locally and calling it" section (in
`CLAUDE.md` or a skill under `.claude/skills/`) covering the `JAVA_HOME`, `SPRING_AI_API_KEY` and
profile requirements, plus the `curl` recipe. Without it every agent session rediscovers the JDK 26
requirement and the dummy-AI-key trick by trial and error, which is most of the friction in practice.

---

## Open questions / gaps

- **Is the Firebase project on legacy Firebase Auth or upgraded to Identity Platform?** This decides
  whether a "disable account creation" user-actions setting is available to close the self-signup hole
  Option B widens. Worth checking in the console before enabling the provider.
- **Should the missing audience validation be fixed regardless?** Adding an `aud == recipai-751ae`
  validator is a few lines and is good practice, but it is independent of this task and would need its
  own decision.
- **Are the Android API keys in `google-services.json` application-restricted?** Only checkable in the
  GCP console. The recommendation above routes around the question rather than answering it.
- **Should an agent be allowed to call `/extract/**` at all?** It spends real Gemini quota, and with
  limits disabled in dev there is no cap to stop a runaway loop. Consider leaving `SPRING_AI_API_KEY`
  as a dummy by default so the failure is loud and cheap.
- **Image endpoints are untested here.** They need real AWS credentials; whether an agent should have
  them is a separate call.
- **Untested claims.** Option A was not implemented or run — no code was changed for this research. The
  Firebase REST flows in Option B were confirmed against documentation, not executed, since the
  provider is not yet enabled.

---

## Appendix: verified local run

```bash
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk
export SPRING_PROFILES_ACTIVE=dev
export SPRING_AI_API_KEY=dummy-key-for-local
cd backend && ./mvnw spring-boot:run
```

Observed:

```
Container backend-postgres-1  Healthy
Successfully validated 21 migrations
Schema "recipai" is up to date. No migration necessary.
Usage limits are DISABLED (recipai.limits.enabled=false)
Tomcat started on port 8080 (http)
Started RecipAiApplication in 4.797 seconds
```

Probed with curl:

| Request                                       | Result                                              |
|-----------------------------------------------|-----------------------------------------------------|
| `GET /actuator/health`                        | `200` `{"status":"UP"}`                             |
| `GET /recipes` (no header)                    | `401`                                               |
| `GET /recipes` with `Bearer not-a-jwt`        | `401`, `error="invalid_token"`, "Malformed token"   |
| `GET /foo`                                    | `401` (from `anyRequest().denyAll()`, not `404`)    |

The app and its PostgreSQL container were both shut down afterwards; no state was left behind.

---

## Sources

### Codebase

- `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — the filter chain, `denyAll` default, `permitAll` actuator.
- `backend/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml` — issuer URI, the mandatory `SPRING_AI_API_KEY`, the dev limits kill-switch, the absent dev datasource.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java` (and the controllers in `planning`, `shoppinglists`, `recipes.collections`, `extraction`) — `jwt.getClaimAsString("email")` as the identity source.
- `backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java` — the existing mock-`JwtDecoder` pattern Option A generalises.
- `backend/pom.xml` — Java 26, `spring-boot-docker-compose` as optional runtime.
- `backend/compose.yaml`, `backend/src/main/java/xyz/stasiak/recipai/config/s3/S3Config.java` — the auto-started database and the lazily-resolved AWS credentials.
- `docs/backend/standards/configuration-profiles.md` — where a dev-only flag is allowed to live.

### External

- [Firebase Auth REST API](https://firebase.google.com/docs/reference/rest/auth) — `accounts:signInWithPassword` and `accounts:signUp` shapes.
- [Using the REST API — Google Identity Platform](https://docs.cloud.google.com/identity-platform/docs/use-rest-api) — exact endpoint URLs, JSON bodies, the API-key requirement, and the secure-token refresh endpoint.
- [Manage User Sessions — Firebase](https://firebase.google.com/docs/auth/admin/manage-sessions) — the fixed one-hour ID token lifetime.
- [Connect to the Authentication Emulator](https://firebase.google.com/docs/emulator-suite/connect_auth) — emulator tokens are unsigned and rejected outside emulators/Admin SDK; basis for rejecting Option C.
- [Learn about and manage API keys for Firebase](https://firebase.google.com/docs/projects/api-keys) — auto-provisioned key restrictions, and where the Web API key lives.
- [Create Custom Tokens — Firebase](https://firebase.google.com/docs/auth/admin/create-custom-tokens) — the service-account requirement behind Option D.
- [Link multiple auth providers to an account — Firebase](https://firebase.google.com/docs/auth/flutter/account-linking) — one-account-per-email behaviour and provider linking.
- [Enable Email/Password sign-in](https://firebase.google.com/docs/auth/web/password-auth) — the console toggle in Option B step 1.
- [`JwtValidators` API docs](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/JwtValidators.html) — `createDefaultWithIssuer` covers issuer and timestamps, not audience.

# Agent Backend Access — Implementation Plan

**Date:** 2026-08-19

Lets a coding agent call the backend locally by adding a dev-profile `JwtDecoder` that treats the bearer
token string as the caller's email. Based on
[`research/agent-http-access-and-auth.md`](research/agent-http-access-and-auth.md), Option A.

## Decisions taken before planning

- **Option A only.** Option B (a Firebase email/password account) is dropped: no provider is enabled, no
  account is created, nothing about production authentication changes. The consequence is that an agent
  can work against `localhost` and nowhere else — there is no way to authenticate against the deployed
  instance at `https://recipai.stasiak.xyz`, and adding one later means revisiting Option B.
- **The bearer token string *is* the email.** `Bearer alice@local.test` and `Bearer bob@local.test` are
  two distinct callers, so sharing and permission flows are exercisable locally with no fixture list.
  **Amended during implementation:** Spring Security's `DefaultBearerTokenResolver` enforces RFC 6750's
  bearer-token grammar (`[a-zA-Z0-9-._~+/]+=*`), which excludes `@`, and rejects `Bearer alice@local.test`
  as malformed before it ever reaches the `JwtDecoder`. The token is therefore *not* required to look like
  an email — any RFC-6750-legal string (`agent`, `alice`, `bob`, ...) is accepted as-is and placed directly
  into the `email` claim, unvalidated. No format check was added.
- **`@Profile("dev")` is the only gate.** No `@ConditionalOnProperty`, no `recipai.security.dev-auth`
  flag, so `application-dev.yml` is untouched. Overrides the research's recommendation of a second gate —
  see Risks.
- **No automated tests.** Verification is the manual `curl` sequence in step 2 — see Risks.
- **No token-helper script, no runbook doc, no skill.** With Option A the token is a constant string, so
  the agent passes it inline.
- **No audience validator.** Raised by the research as an open question and independent of this task.
  Firebase Secure Token issues per-project, so `aud` (`recipai-751ae`) and `iss`
  (`https://securetoken.google.com/recipai-751ae`) carry identical information; the existing issuer check
  already pins tokens to the project and an `aud` validator would reject nothing it accepts.

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/backend/standards/java-patterns.md` — class visibility rules for the new config class.
- `docs/backend/standards/module-structure.md` — the `@Slf4j` logging pattern.
- `docs/backend/standards/configuration-profiles.md` — confirms `prod` is the default active profile,
  which is what makes a profile-only gate viable.

**Design & ADRs**
- `research/agent-http-access-and-auth.md` > "The identity model" — why one `email` claim is sufficient.
- `research/agent-http-access-and-auth.md` > "Option A" — the mechanism being built.
- `research/agent-http-access-and-auth.md` > "Appendix: verified local run" — the exact env the local
  boot needs (`JAVA_HOME`, `SPRING_AI_API_KEY`).
- _No ADR covers this area._

**Code to mirror**
- `backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java` — how a `Jwt` is hand-built
  claim by claim; the dev decoder is this pattern without the Mockito fixture list.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` — the `@PostConstruct` `log.warn`
  kill-switch announcement to copy for the bypass warning.
- `backend/src/main/java/xyz/stasiak/recipai/config/time/TimeConfig.java` — package-private
  `@Configuration` with a package-private `@Bean` method; the `Clock` bean to inject.
- `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — the filter chain the
  new decoder plugs into.

## File inventory

- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/config/security/DevAuthConfig.java` —
  dev-profile `JwtDecoder` minting a caller from the token string.

That is the whole change. No config, migration, dependency or test files.

## Step-by-step plan

### 1. Add the dev `JwtDecoder`

Create `DevAuthConfig` in `config/security/`, package-private, `@Slf4j`, annotated `@Profile("dev")`.
`application.yml` sets `spring.profiles.active: prod`, so the bean does not exist unless someone
explicitly asks for the `dev` profile.

The bean is a lambda `JwtDecoder`:

- No format check: any token that reaches the decoder (i.e. any RFC-6750-legal bearer token — the `@`
  character never reaches this code, see decision above) is accepted as-is.
- Build `Jwt.withTokenValue(token)` with `header("alg", "none")`, `subject(token)`,
  `claim("email", token)`, `claim("email_verified", true)`, the real `issuer-uri`, and
  `issuedAt`/`expiresAt` one hour apart, taken from the injected `Clock` bean.
- `@PostConstruct` `log.warn` announcing the bypass, mirroring `LimitsFacade.warnWhenDisabled()` —
  e.g. `AUTHENTICATION BYPASS ENABLED (dev profile) - every bearer token is accepted as the caller's
  email`.

Registering a `JwtDecoder` bean makes Spring Boot's `OAuth2ResourceServerJwtConfiguration` back off
(`@ConditionalOnMissingBean(JwtDecoder.class)`), so no JWKS fetch happens at startup and the app boots
offline. Under `prod` the bean is absent and the normal Firebase decoder is built as today.

The existing test suites are unaffected: they run under the default `prod` profile, so this bean never
loads and `TestSecurityConfiguration`'s `@Primary` decoder still wins with no bean conflict.

- Files: `backend/src/main/java/xyz/stasiak/recipai/config/security/DevAuthConfig.java`
- Verify: `./mvnw -q compile` succeeds, then `./mvnw test` — the full existing suite stays green.

### 2. Manual end-to-end check

```bash
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk
export SPRING_PROFILES_ACTIVE=dev
export SPRING_AI_API_KEY=dummy-key-for-local
cd backend && ./mvnw spring-boot:run
```

- Files: _none._
- Verify, in order:
  - Startup log contains the bypass `WARN`.
  - `curl -s localhost:8080/actuator/health` → `{"status":"UP"}` (no token needed).
  - `curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer agent" localhost:8080/recipes` → `200`.
  - Same call with no header → `401`. (A token containing `@`, e.g. `Bearer agent@local.test`, also
    returns `401`, but from Spring's bearer-token grammar check, not from `DevAuthConfig` — see the
    amended decision above.)
  - Two-user scoping: create a recipe as `alice`, then `GET /recipes` as `bob` returns a list without it
    and as `alice` returns it:

```bash
curl -s -X POST localhost:8080/recipes \
  -H "Authorization: Bearer alice" -H 'Content-Type: application/json' \
  -d '{"name":"Scoping probe","data":{"ingredients":[{"name":"salt"}],"instructions":[{"step":"mix"}]}}'
```

  - Boot once with **no network route to Google** and confirm startup still succeeds, proving the JWKS
    fetch is genuinely skipped.
  - `grep -n "profiles" backend/src/main/resources/application.yml` still shows `active: prod`, i.e. the
    bypass stays off by default.

## Test plan

**Unit tests**
- _N/A — no automated tests by decision; see Risks._

**Integration tests**
- _N/A — same. The existing suites must still pass unchanged, which is step 1's verify._

**Flutter widget/integration tests**
- _N/A — no mobile code changes._

**Manual verification**
- Step 2 in full. This is the only verification the change gets.

## Verification checklist

- [x] `./mvnw test` — full existing suite green, no test file added or modified
- [ ] Every check in step 2 passes, including the offline boot and the two-user scoping probe — all
      checked except the offline boot, which the user is verifying independently
- [x] `spring.profiles.active: prod` is still the default in `application.yml`
- [x] The bypass `WARN` appears under `dev` and nowhere else
- [x] No new compiler warnings

## Risks surfaced during planning

- **Risk:** with `@ConditionalOnProperty` dropped, the `dev` profile alone arms an authentication bypass
  that ships inside the production jar.
  **Why it matters:** the research recommended a second gate precisely so that activating `dev` for an
  unrelated reason — reproducing a bug, pointing a local run at a remote database, a container image
  built with the wrong `SPRING_PROFILES_ACTIVE` — cannot also turn every bearer token into a valid
  identity for any email. One environment variable now separates a normal dev run from an open backend.
  **Mitigation:** `prod` is the default active profile and the startup `WARN` makes an armed instance
  loud. Flagged for the record; the single gate is the accepted decision.

- **Risk:** no automated test covers the bypass.
  **Why it matters:** nothing fails if a later refactor drops `@Profile("dev")`, widens the email check,
  or lets the bean load under `prod` — the change is silent and the manual `curl` sequence is only run
  once, now. This is the same class of regression the profile gate is the sole defence against.
  **Mitigation:** none in this plan. Worth revisiting if the decoder ever grows beyond the ~20 lines
  described in step 1.

- **Risk:** the offline/back-off claim is untested. The research never implemented Option A.
  **Why it matters:** if `OAuth2ResourceServerJwtConfiguration` does not back off as expected, startup
  still resolves the Firebase issuer and the agent loses the offline property that motivates Option A.
  **Mitigation:** the no-network boot in step 2 checks it explicitly rather than assuming it.

- **Risk:** `docs/backend/modules/config/codebase_structure.md` lists the files under `config/security/`
  and will be stale once `DevAuthConfig.java` lands.
  **Why it matters:** the file tree silently drifts from reality.
  **Mitigation:** out of scope here by the project's code-not-docs planning rule; fold it into the
  `docs-updating` pass after implementation.

- **Risk (pre-existing, out of scope).** Identity *is* the email, so a user changing their Google email
  address silently loses access to all their data. Surfaced by the research; noted here so it is not lost.

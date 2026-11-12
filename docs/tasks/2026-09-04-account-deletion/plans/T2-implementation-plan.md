# T2: Admin deletion endpoint — Implementation Plan

**Date:** 2026-11-12

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/backend/standards/configuration-profiles.md` — the allowlist lives in `application-dev.yml`
  and an env var in `application-prod.yml`, never `application.yml`; the suite inherits `dev`.
- `docs/backend/standards/integration-tests.md` — `@IntegrationTest` uniformity (no
  `properties =`, `@TestPropertySource` or `@Import`), `TestIdentities.freshToken()`,
  `TestRestClients.forToken`, seeding through the API, `shouldXxxWhenYyy` naming.
- `docs/backend/standards/module-structure.md` > SLF4J Logging Pattern — `log.debug` on handler
  entry, `log.info` for the state change.
- `docs/backend/standards/java-patterns.md` — records and class visibility for `AdminProperties`,
  `AdminAuthoritiesConverter` and `AdminController`.

**Design & ADRs**
- `docs/tasks/2026-09-04-account-deletion/plans/T2-task-design.md` — the whole document, especially
  Interfaces, Data flow, Pseudo-code and Assumptions to verify.
- `docs/ADRs/0011-admin-authority-from-config-allowlist.md` — one grant seam, enforcement as a
  chain matcher, refusal without revealing the route.
- `docs/tasks/2026-09-04-account-deletion/HLD.md` > Feature areas > Backend — administrator
  identification — the behaviours the endpoint owes.
- `docs/tasks/2026-09-04-account-deletion/research/admin-role-concept.md` > Option B — why
  authorities are empty today and where the converter plugs in.

**Code to mirror**
- `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — the chain being
  extended; matcher list and `oauth2ResourceServer` block.
- `backend/src/main/java/xyz/stasiak/recipai/config/security/DevAuthConfig.java` — the dev decoder
  the converter runs after; `Bearer admin` becomes `admin@local.test` with `email_verified: true`.
- `backend/src/main/java/xyz/stasiak/recipai/config/s3/S3Properties.java` and `S3Config.java` —
  the `@ConfigurationProperties` record + `@EnableConfigurationProperties` pattern.
- `backend/src/main/java/xyz/stasiak/recipai/settings/SettingsController.java` — sibling
  controller: package-private, `@RequiredArgsConstructor`, `@Slf4j`, `ResponseEntity.noContent()`.
- `backend/src/test/java/xyz/stasiak/recipai/settings/AccountDeletionIntegrationTest.java` — the
  seeding/listing helpers, the unauthenticated-`403` (CSRF) case and its `try/fail/catch` style.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitPeriodTest.java` — plain-JUnit unit test
  shape (no Spring, AssertJ, given/when/then spacing).
- `backend/http/settings.http` — the destructive-request comment style to extend.

## File inventory

- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/config/security/AdminProperties.java` —
  package-private `recipai.security` record; lowercased admin email set.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/config/security/AdminAuthoritiesConverter.java`
  — grants `ROLE_ADMIN` to verified, allowlisted email claims; the single grant seam.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` —
  `/admin/**` `hasRole("ADMIN")` matcher, `JwtAuthenticationConverter` bean, enable `AdminProperties`.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/settings/AdminController.java` —
  `DELETE /admin/accounts?email=…`, validates, logs, delegates to `AccountDeletionService`.
- **MODIFY** `backend/src/main/resources/application-dev.yml` — `recipai.security.admin-emails: admin@local.test`.
- **MODIFY** `backend/src/main/resources/application-prod.yml` —
  `recipai.security.admin-emails: ${RECIPAI_SECURITY_ADMIN_EMAILS:}`.
- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/config/security/AdminAuthoritiesConverterTest.java`
  — plain JUnit over hand-built `Jwt`s: verified/allowlist/case/empty cases.
- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/settings/AdminAccountDeletionIntegrationTest.java`
  — admin purge, non-admin `403`, validation `400`s, unauthenticated refusal.
- **MODIFY** `backend/http/settings.http` — destructive admin deletion request as `Bearer admin`.

Not touched: `DevAuthConfig.java`, `AccountDeletionService.java`, `SettingsController.java`,
`application.yml`, `application-test.yml`, `pom.xml` (validation and resource-server starters are
already present), no migration.

## Step-by-step plan

1. **Admin allowlist and authority converter** — create `AdminProperties` exactly per the task
   design's interface (`@DefaultValue Set<String> adminEmails`, compact constructor lowercasing
   with `Locale.ROOT` into an unmodifiable set). Create `AdminAuthoritiesConverter` per the
   pseudo-code, with `static final String ADMIN_ROLE = "ADMIN"`. Neither is a Spring bean yet.
   Write the converter's unit test.
   - Files: `config/security/AdminProperties.java`, `config/security/AdminAuthoritiesConverter.java`,
     `src/test/.../config/security/AdminAuthoritiesConverterTest.java`
   - Verify: from `backend/`, `./mvnw test -Dtest=AdminAuthoritiesConverterTest` passes.

2. **Wire the authority into the chain** — on `SecurityConfig` add
   `@EnableConfigurationProperties(AdminProperties.class)`; add a `@Bean JwtAuthenticationConverter
   jwtAuthenticationConverter(AdminProperties properties)` that calls
   `setJwtGrantedAuthoritiesConverter(new AdminAuthoritiesConverter(properties))` — construct it
   inline, do not make it a `@Component` (see Risks); inject it into `filterChain` and replace
   `.jwt(Customizer.withDefaults())` with `.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))`
   (drop the now-unused `Customizer` import). Add
   `.requestMatchers("/admin/**").hasRole(AdminAuthoritiesConverter.ADMIN_ROLE)` immediately before
   `.anyRequest().denyAll()`; leave `/users/**` and every other matcher as is. Add the allowlist to
   `application-dev.yml` (`admin@local.test`) and `application-prod.yml`
   (`${RECIPAI_SECURITY_ADMIN_EMAILS:}`) under the existing `recipai:` key.
   - Files: `config/security/SecurityConfig.java`, `resources/application-dev.yml`,
     `resources/application-prod.yml`
   - Verify: `./mvnw test -Dtest='RecipAiApplicationTests,AccountDeletionIntegrationTest'` passes —
     the context loads and the existing self-service path is unaffected by the converter swap.

3. **Admin endpoint** — create `AdminController` per the interface: package-private,
   `@RestController @RequestMapping("/admin") @RequiredArgsConstructor @Slf4j`,
   `@DeleteMapping("/accounts")` taking `@RequestParam @NotBlank @Email String email` and
   `@AuthenticationPrincipal Jwt jwt`. Read the operator from `jwt.getClaimAsString("email")`, log
   `log.info("Admin {} deleting account {}", operator, email)`, call
   `accountDeletionService.deleteAccount(email)` with the email unchanged (no lowercasing), return
   `ResponseEntity.noContent().build()`. No `@Validated`, no exception handler (task design >
   Decisions made) unless step 4 disproves assumption 1.
   - Files: `settings/AdminController.java`
   - Verify: `./recipai.sh start-backend`, then
     `curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE -H "Authorization: Bearer admin" 'localhost:8080/admin/accounts?email=nobody@local.test'`
     prints `204`; the same with `Bearer bob` prints `403`; with `Bearer admin` and `email=not-an-email`
     prints `400`. `./recipai.sh stop-backend`.

4. **Integration suite** — write `AdminAccountDeletionIntegrationTest` per the Test plan: one
   fresh `target` and one fresh `nonAdmin` token per test, the fixed `"admin"` token for the
   administrator (a class constant with a comment that it owns nothing, so sharing it leaks no
   state). Copy only the seeding/listing helpers the cases need from
   `AccountDeletionIntegrationTest`; call the endpoint with `.uri(b -> b.path("/admin/accounts").queryParam("email", email).build())`
   so the `@` is encoded consistently.
   - Files: `src/test/.../settings/AdminAccountDeletionIntegrationTest.java`
   - Verify: `./mvnw test -Dtest=AdminAccountDeletionIntegrationTest` passes. If a 400 case gets
     `500` or `204`, apply assumption 1's fallback (`@Validated` on `AdminController`) and re-run.

5. **Local request suite** — append to `settings.http` an `@admin = admin` variable at the top and
   a `### Delete any account by email - DESTRUCTIVE: …` request:
   `DELETE {{baseUrl}}/admin/accounts?email={{user}}@local.test` with
   `Authorization: Bearer {{admin}}`.
   - Files: `backend/http/settings.http`
   - Verify: the request returns `204` from the IDE against a local `dev` backend; then run
     `tasks.md` > T2 "How to verify" end-to-end (seed as `Bearer alice`, admin-delete
     `alice@local.test`, list endpoints empty for alice; `Bearer bob` gets `403` and alice's freshly
     re-seeded recipe survives).

## Test plan

**Unit tests**
- `AdminAuthoritiesConverterTest` (allowlist `{"admin@example.com"}` unless the case says otherwise):
  - `shouldGrantAdminWhenEmailIsAllowlistedAndVerified` — result is exactly `[ROLE_ADMIN]`
  - `shouldNotGrantAdminWhenEmailIsNotVerified` — `@ParameterizedTest` over `email_verified: false`
    and the claim absent; result empty
  - `shouldNotGrantAdminWhenEmailIsNotAllowlisted` — verified `other@example.com`; empty
  - `shouldNotGrantAdminWhenEmailClaimIsMissing` — verified, no `email` claim; empty, no exception
  - `shouldGrantAdminWhenAllowlistDiffersOnlyInCase` — allowlist `{"Admin@Example.COM"}` via
    `new AdminProperties(...)`, token `admin@example.com`; and token in mixed case against a
    lowercase allowlist
  - `shouldNotGrantAdminWhenAllowlistIsEmpty` — `new AdminProperties(Set.of())`; empty

**Integration tests**
- `AdminAccountDeletionIntegrationTest` (`@IntegrationTest`):
  - `shouldDeleteNamedAccountWhenCallerIsAdmin` — seed recipe, collection, shopping list and meal
    plan as `target`; admin call returns `204`; all four list endpoints empty for `target`
  - `shouldReturn403AndKeepDataWhenCallerIsNotAdmin` — `nonAdmin` names `target`'s email → `403`;
    `target`'s recipe still lists
  - `shouldReturn403WhenNonAdminTargetsOwnEmail` — `nonAdmin` names its own email after seeding a
    recipe → `403`; recipe still lists
  - `shouldReturn204WhenNamedAccountOwnsNothing` — admin names a fresh, never-used email → `204`
  - `shouldReturn400WhenEmailIsMissing` — admin, no query parameter → `400`
  - `shouldReturn400WhenEmailIsMalformed` — `@ParameterizedTest` over `"not-an-email"` and `"  "`
    as admin → `400`; for the malformed case, a seeded `target` recipe still lists afterwards
  - `shouldReturn403WhenNonAdminSendsMalformedEmail` — `nonAdmin` with `email=not-an-email` gets
    `403`, not `400` (the chain refuses before validation; the route is not revealed)
  - `shouldRejectUnauthenticatedAdminDeletion` — no `Authorization` header → `403` (CSRF before
    authentication, as in `AccountDeletionIntegrationTest`); assert `401` instead if that is what
    holds, and record it under assumption 4

**Flutter widget/integration tests**
- _N/A — backend-only task._

**Manual verification**
- `tasks.md` > T2 "How to verify" against a local `dev` backend (step 5).

## Verification checklist

- [ ] `./mvnw verify` passes from `backend/` — whole suite, including both ArchUnit classes
- [ ] No `properties =`, `@TestPropertySource` or ad hoc `@Import` added to an `@IntegrationTest`
      class (the admin allowlist comes from `application-dev.yml`, so no context fork)
- [ ] `AdminAuthoritiesConverter` is not a Spring bean; the only new beans are `AdminProperties`
      and the `JwtAuthenticationConverter`
- [ ] `tasks.md` > T2 "How to verify" succeeds end-to-end against a local `dev` backend
- [ ] Task design's Assumptions to verify: 1 (method validation → `400`) settled by the `400`
      cases; 2 (empty env var → empty set) deferred — untested, and harmless to authorisation if
      wrong, per the task design; 3 (no `SCOPE_`
      authority relied on) — `grep -rn "SCOPE_\|hasAuthority\|PreAuthorize" backend/src` still
      returns nothing; 4 (unauthenticated → `403`) settled by
      `shouldRejectUnauthenticatedAdminDeletion`
- [ ] Happy-path `INFO` logs are `Admin admin@local.test deleting account …` followed by the two
      `AccountDeletionService` lines — no stack traces, no `ERROR`; a `403` logs nothing from
      `AdminController`
- [ ] No new compiler warnings; `DevAuthConfig`, `AccountDeletionService` and `pom.xml` unchanged

## Risks surfaced during planning

- **Risk:** Spring Boot registers every `Converter` bean in the context with the MVC
  `FormattingConversionService`.
  **Why it matters:** if `AdminAuthoritiesConverter` were annotated `@Component` (the natural
  reflex), it would also become a web conversion candidate for `Jwt` → `Collection`, a side effect
  nobody intends from a security class.
  **Mitigation:** construct it with `new` inside the `jwtAuthenticationConverter` bean method
  (step 2); the checklist gates it.

- **Risk:** `AdminProperties` is package-private, unlike the public `S3Properties` it mirrors.
  **Why it matters:** constructor binding of a non-public record under
  `@EnableConfigurationProperties` is less common in this codebase; a binding failure would fail
  every `@IntegrationTest` at context load and read as an unrelated mass failure.
  **Mitigation:** step 2 ends on `RecipAiApplicationTests`, the first point binding runs, so a
  failure surfaces there. If binding fails, make the record public — it stays in
  `config.security`, which no module depends on.

- **Risk:** the task design lists a *Documentation* deliverable (`settings/api.md`,
  `settings/module.md`, `architecture.md`, `local-development.md`, `docs/INDEX.md`).
  **Why it matters:** this plan covers code only (the `.http` file is the one non-Java deliverable
  kept); dropping the docs here would lose them.
  **Mitigation:** run `docs-updating` after the implementation lands, with the task design's
  Documentation section as its input — the same handling as T1.

- **Risk:** the design's test list does not cover the missing-`email` claim or a non-admin sending
  a malformed email.
  **Why it matters:** the last one is the observable proof of "refused rather than told the route
  exists" — a `400` there would leak the route.
  **Mitigation:** added to the Test plan (`shouldNotGrantAdminWhenEmailClaimIsMissing`,
  `shouldReturn403WhenNonAdminSendsMalformedEmail`);
  they are additions to the design, not changes to it.

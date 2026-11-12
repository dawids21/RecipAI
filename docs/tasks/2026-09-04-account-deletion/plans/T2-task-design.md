# T2: Admin deletion endpoint — Task Design

**Date:** 2026-11-12

## Summary

An allowlist of administrator emails, bound from configuration, is turned into a `ROLE_ADMIN`
authority by a JWT authorities converter in `config.security`; a new `/admin/**` matcher in the
existing chain requires that role. A new `AdminController` in `settings` exposes
`DELETE /admin/accounts?email=…` and runs T1's `AccountDeletionService.deleteAccount(email)`
unchanged. No migration, no new dependency, no change to the purge.

## Components and responsibilities

### Modified — security (`backend/src/main/java/xyz/stasiak/recipai/config/security/`)

- **`AdminProperties`** (CREATE) — `@ConfigurationProperties(prefix = "recipai.security")` record
  holding `adminEmails`, lowercased in the compact constructor; empty when unset. Enabled with
  `@EnableConfigurationProperties` on `SecurityConfig`, the way `S3Config` enables `S3Properties`.
- **`AdminAuthoritiesConverter`** (CREATE) — package-private
  `Converter<Jwt, Collection<GrantedAuthority>>`, the single place the admin authority is granted
  (ADR-0011). Returns `ROLE_ADMIN` when the lowercased `email` claim is in the allowlist **and**
  `email_verified` is `true`; otherwise an empty collection, as every request gets today.
- **`SecurityConfig`** (MODIFY) — adds `.requestMatchers("/admin/**").hasRole("ADMIN")` before
  `.anyRequest().denyAll()`, and replaces `jwt(Customizer.withDefaults())` with a
  `JwtAuthenticationConverter` whose granted-authorities converter is `AdminAuthoritiesConverter`.
  `/users/**` and the other matchers are untouched.

`DevAuthConfig` is **not** modified: it only supplies the decoder, and the converter runs after it in
every profile, so a dev caller becomes an administrator by having an allowlisted address.

### Modified — `settings` (`backend/src/main/java/xyz/stasiak/recipai/settings/`)

- **`AdminController`** (CREATE) — package-private `@RestController` on `/admin`. Takes the target
  email as a validated query parameter, logs the operator and the target, calls
  `AccountDeletionService.deleteAccount`, returns `204`. Holds no authorisation logic — the chain
  has already refused every non-admin before it is reached.
- **`AccountDeletionService`** — unchanged (T1).

### Configuration

- **`application-dev.yml`** (MODIFY) — `recipai.security.admin-emails: admin@local.test`, so
  `Bearer admin` is the dev administrator. The test suite inherits it through the `dev` profile it
  already activates; `application-test.yml` is not touched (it holds only overrides of `dev`).
- **`application-prod.yml`** (MODIFY) — `recipai.security.admin-emails: ${RECIPAI_SECURITY_ADMIN_EMAILS:}`,
  comma-separated, empty by default.

### Tests

- **`AdminAuthoritiesConverterTest`** (CREATE, `src/test/java/xyz/stasiak/recipai/config/security/`)
  — plain JUnit over hand-built `Jwt`s. Needed because `DevAuthConfig` always sets
  `email_verified: true`, so the unverified case cannot be reached through HTTP. Cases:
  - `shouldGrantAdminWhenEmailIsAllowlistedAndVerified`
  - `shouldNotGrantAdminWhenEmailIsNotVerified` (claim `false`, and claim absent)
  - `shouldNotGrantAdminWhenEmailIsNotAllowlisted`
  - `shouldGrantAdminWhenAllowlistDiffersOnlyInCase`
  - `shouldNotGrantAdminWhenAllowlistIsEmpty`
- **`AdminAccountDeletionIntegrationTest`** (CREATE, `src/test/java/xyz/stasiak/recipai/settings/`)
  — `@IntegrationTest`; the target is a fresh subject per test, the administrator is the fixed
  `admin` token (it owns nothing, so sharing it across tests leaks no state). Cases:
  - `shouldDeleteNamedAccountWhenCallerIsAdmin` — seed a recipe, collection, shopping list and meal
    plan as the target; after the admin call all four list endpoints are empty for the target. The
    purge's own end state is T1's suite's job, so this asserts only that it ran.
  - `shouldReturn403AndKeepDataWhenCallerIsNotAdmin` — a fresh non-admin caller gets `403`; the
    target's recipe still lists.
  - `shouldReturn403WhenNonAdminTargetsOwnEmail` — the admin route is not a second self-service path.
  - `shouldReturn204WhenNamedAccountOwnsNothing`
  - `shouldReturn400WhenEmailIsMissing`, `shouldReturn400WhenEmailIsMalformed`

### Documentation

- **`docs/backend/modules/settings/api.md`** (MODIFY) — the `DELETE /admin/accounts` contract and
  the `/admin/**` matcher.
- **`docs/backend/modules/settings/module.md`** (MODIFY) — description widened to admin deletion,
  `AdminController` and the new test in the file tree, the admin logging line.
- **`docs/project/architecture.md`** (MODIFY) — `RECIPAI_SECURITY_ADMIN_EMAILS` in the production
  env-var table (optional; empty means no administrator); the `config.security` line gains the admin
  authority.
- **`docs/project/local-development.md`** (MODIFY) — `Auth` section: `Bearer admin` is the dev
  administrator.
- **`docs/INDEX.md`** (MODIFY) — the settings `api.md` entry lists `DELETE /admin/accounts`.
- **`backend/http/settings.http`** (MODIFY) — an admin deletion request as `Bearer admin`, marked
  destructive like the existing one.

## Interfaces and method signatures

```java
// config.security
@ConfigurationProperties(prefix = "recipai.security")
record AdminProperties(@DefaultValue Set<String> adminEmails) {
    AdminProperties { /* lowercase every entry */ }
}

class AdminAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    static final String ADMIN_ROLE = "ADMIN";             // hasRole(ADMIN_ROLE) ⇔ ROLE_ADMIN
    AdminAuthoritiesConverter(AdminProperties properties);
    Collection<GrantedAuthority> convert(Jwt jwt);
}

class SecurityConfig {
    SecurityFilterChain filterChain(HttpSecurity http);    // + /admin/** matcher, + converter
    JwtAuthenticationConverter jwtAuthenticationConverter(AdminProperties properties);
}
```

```java
// settings
@RestController @RequestMapping("/admin")
class AdminController {
    @DeleteMapping("/accounts")
    ResponseEntity<Void> deleteAccount(
            @RequestParam @NotBlank @Email String email,
            @AuthenticationPrincipal Jwt jwt);                 // 204
}
```

## Data flow

1. `DELETE /admin/accounts?email=alice@gmail.com` with a bearer token.
2. The decoder validates the token (Firebase in prod, `DevAuthConfig` in dev and tests).
3. `JwtAuthenticationConverter` → `AdminAuthoritiesConverter` yields `[ROLE_ADMIN]` or `[]`. The
   principal name stays the `sub` claim, as today.
4. The chain's `/admin/**` matcher: no `ROLE_ADMIN` → `403`, before the controller is reached and
   before parameter validation runs, so a non-admin learns nothing about the route. This is the
   same `403` an authenticated caller gets from `denyAll` on an unmapped path.
5. `AdminController` validates `email` (missing / blank / malformed → `400`), logs
   `"Admin {} deleting account {}"` at INFO, and calls `AccountDeletionService.deleteAccount(email)`.
6. T1's purge runs against the target exactly as for self-service, its owner checks passing because
   they are against the subject, not the caller (`HLD.md` > Approach > Chosen).
7. `204`, also when the email owns nothing — with no users table, "unknown account" and "empty
   account" are the same thing.

Failure part-way through the purge behaves as in T1: the caller gets the failing module's status and
the repair is to call again.

## Pseudo-code

```
AdminAuthoritiesConverter.convert(jwt):
    email = jwt.getClaimAsString("email")
    verified = jwt.getClaimAsBoolean("email_verified")    # null when absent
    if email == null or verified != TRUE: return []
    if email.toLowerCase(Locale.ROOT) in properties.adminEmails:
        return [SimpleGrantedAuthority("ROLE_" + ADMIN_ROLE)]
    return []
```

Matcher order in `SecurityConfig` — `/admin/**` must precede `anyRequest().denyAll()`; it does not
overlap any existing matcher, so its position among the others does not matter.

## Decisions made

- **`DELETE /admin/accounts?email=…`** — a dedicated `/admin/**` prefix is one matcher that any future
  privileged endpoint reuses (ADR-0011); the email travels as a query parameter.
- **Controller lives in `settings`, named `AdminController`** — `settings` owns account deletion and
  `AccountDeletionService` is package-private there; the URL prefix and the module name intentionally
  differ.
- **`/users/**` is left as it is** — removing the dormant matcher is unrelated to this task.
- **Admin requires `email_verified == true`** — closes the hole an unverified token carrying an
  admin's address would open if a non-Google provider were ever enabled; costs one condition.
- **Allowlist compared case-insensitively; the target email is passed through unchanged** — a
  case typo in the env var must not lock the operator out, while the purge must match stored rows
  exactly as T1 does.
- **Empty allowlist fails closed, silently** — the app starts with no administrator and every
  `/admin/**` call is a `403`.
- **Dev administrator is a dedicated `admin` caller, in `application-dev.yml` only** — keeps the
  everyday seed users non-admin; the suite inherits it through the `dev` profile.
- **The grant is an authorities converter inside the default `JwtAuthenticationConverter`**, not a
  whole custom authentication converter — principal naming stays as it is, and the default `SCOPE_`
  mapping it replaces maps nothing for Firebase tokens.
- **No re-authentication or second factor on the admin endpoint** — the HLD settles the guard as the
  authority alone.
- **The operator and the target are logged at INFO** — consistent with the grant/share/invite lines;
  the HLD accepts emails in logs, and nothing else is retained.
- **No exception handler for `settings`** — the 400s come from Spring MVC's own validation and
  missing-parameter handling, and the module still raises nothing of its own.

## Assumptions to verify

- **Assumption:** Spring MVC's built-in method validation (6.1+) applies `@NotBlank @Email` to a
  `@RequestParam` without `@Validated` on the controller and maps the failure to `400` with no
  project handler.
  **If wrong:** a malformed email reaches the purge, which deletes nothing and returns `204`, or the
  failure surfaces as `500`. Fix with `@Validated` on the controller or a `SettingsExceptionHandler`.
- **Assumption:** `${RECIPAI_SECURITY_ADMIN_EMAILS:}` resolving to an empty string binds to an empty
  `Set`, not to a set holding `""`.
  **If wrong:** harmless to authorisation (no token has an empty email), but the compact constructor
  should drop blank entries.
- **Assumption:** Firebase ID tokens never carry a `scope`/`scp` claim, so replacing the default
  granted-authorities converter removes no authority anything relies on.
  **If wrong:** some `SCOPE_` authority disappears — nothing checks one today, so nothing breaks yet.
- **Assumption:** an unauthenticated `DELETE /admin/accounts` is refused with `403` by CSRF before
  authentication, as T1's suite documents for `/settings/account`.
  **If wrong:** it is a `401`; either way it is refused, and the test should assert whichever holds.

## Required reading

- `docs/ADRs/0011-admin-authority-from-config-allowlist.md` — the decision: allowlist, one grant
  seam, enforcement as a chain matcher.
- `HLD.md` > Feature areas > Backend — administrator identification — the behaviours the endpoint
  and the authority owe.
- `research/admin-role-concept.md` > Option B — the converter seam and why authorities are empty
  today.
- `backend/…/config/security/SecurityConfig.java` and `DevAuthConfig.java` — the chain being extended
  and the decoder the converter runs after.
- `backend/…/config/s3/S3Properties.java`, `S3Config.java` — the `@ConfigurationProperties` pattern
  to mirror.
- `backend/…/settings/SettingsController.java`, `AccountDeletionService.java` — the sibling controller
  and the purge reused unchanged.
- `docs/backend/standards/configuration-profiles.md` — where the allowlist may and may not live.
- `docs/backend/standards/integration-tests.md` — fresh subjects, `TestRestClients`, seeding through
  the API.

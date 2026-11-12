# Config Module

Cross-cutting infrastructure configuration: OAuth2 Resource Server / JWT validation including the
admin authority (`config.security`), AWS S3 client configuration and the object-storage seam
`recipes.images` consumes (`config.s3`), and the `Clock` bean time-dependent services read instead of
the system clock (`config.time`).

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── config/
    ├── s3/
    │   ├── S3Config.java            # S3 client and presigner bean configuration
    │   ├── S3Properties.java        # S3 configuration properties (bucket, region, presigned URL expiration)
    │   ├── S3Service.java           # Public object-storage seam with no AWS type in any signature; the substitution point for tests
    │   ├── AwsS3Service.java        # Package-private S3Service implementation over the AWS SDK; owns the bucket name
    │   └── S3StorageException.java  # Public seam failure type, caught by recipes.images.RecipeImagesService
    ├── security/
    │   ├── AdminAuthoritiesConverter.java  # The single place ROLE_ADMIN is granted — see Administrator authority
    │   ├── AdminProperties.java     # recipai.security.admin-emails allowlist, lowercased
    │   ├── DevAuthConfig.java       # Dev-profile JwtDecoder — bearer token becomes the caller <token>@local.test
    │   └── SecurityConfig.java      # OAuth2 Resource Server configuration and the /admin/** role matcher
    └── time/
        └── TimeConfig.java          # Clock bean (system UTC) for time-dependent services
```

```
backend/src/test/java/xyz/stasiak/recipai/
└── config/
    └── security/
        └── AdminAuthoritiesConverterTest.java   # Grant rules over hand-built tokens, incl. the unverified email unreachable via dev auth
```

Its test-side `S3Service` stand-in is shared test infrastructure — see
`docs/backend/standards/integration-tests.md` → Shared Test Infrastructure.

## Administrator authority

A caller holds `ROLE_ADMIN` when the token's `email` claim, compared case-insensitively, is in the
`recipai.security.admin-emails` allowlist **and** `email_verified` is `true`; every other caller holds no
authority. `/admin/**` requires the role (see
`docs/ADRs/0011-admin-authority-from-config-allowlist.md`).

- **Where the allowlist comes from** — `admin@local.test` in `application-dev.yml`, so `Bearer admin` is
  the dev and test-suite administrator; in production `RECIPAI_SECURITY_ADMIN_EMAILS` (see
  `docs/project/architecture.md`). An empty allowlist means no administrator and every `/admin/**` call is
  a 403.
- **Gotcha** — the admin converter replaces Spring's default granted-authorities converter, so no
  `SCOPE_` authority is ever derived from a token. Firebase ID tokens carry no scope claim, so nothing is
  lost today, but a check on a `SCOPE_` authority would never pass.

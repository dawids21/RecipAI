# Config Module

Cross-cutting infrastructure configuration: OAuth2 Resource Server / JWT validation
(`config.security`), AWS S3 client configuration and the object-storage seam `recipes.images`
consumes (`config.s3`), and the `Clock` bean time-dependent services read instead of the system
clock (`config.time`).

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
    │   ├── DevAuthConfig.java       # Dev-profile JwtDecoder — bearer token becomes the caller <token>@local.test
    │   └── SecurityConfig.java      # OAuth2 Resource Server configuration
    └── time/
        └── TimeConfig.java          # Clock bean (system UTC) for time-dependent services
```

The module has no tests of its own. Its test-side `S3Service` stand-in is shared test infrastructure — see
`docs/backend/standards/integration-tests.md` → Shared Test Infrastructure.

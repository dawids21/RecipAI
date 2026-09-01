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
    │   ├── S3Properties.java        # S3 configuration properties (bucket name, region, presigned URL expiration)
    │   ├── S3Service.java           # Public object-storage seam — four methods (put, delete, list, presign), no AWS type in any signature; the substitution point for tests
    │   ├── AwsS3Service.java        # Package-private @Service implementing S3Service — delegates to S3Client/S3Presigner, owns the bucket name, translates S3Exception
    │   └── S3StorageException.java  # Public seam failure type, raised by AwsS3Service and caught by recipes.images.RecipeImagesService
    ├── security/
    │   ├── DevAuthConfig.java       # Dev-profile JwtDecoder — bearer token becomes the caller <token>@local.test
    │   └── SecurityConfig.java      # OAuth2 Resource Server configuration
    └── time/
        └── TimeConfig.java          # Clock bean (system UTC) for time-dependent services
```

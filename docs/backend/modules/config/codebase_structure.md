# Config Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── config/
    ├── s3/
    │   ├── S3Config.java            # S3 client and presigner bean configuration
    │   └── S3Properties.java        # S3 configuration properties (bucket name, region, presigned URL expiration)
    └── security/
        ├── DevAuthConfig.java       # Dev-profile JwtDecoder — bearer token becomes the caller <token>@local.test
        └── SecurityConfig.java      # OAuth2 Resource Server configuration
    └── time/
        └── TimeConfig.java          # Clock bean (system UTC) for time-dependent services
```

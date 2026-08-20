# Configuration Profiles

The backend uses Spring Boot profiles to support different deployment environments. By default the application runs in **production** mode.

## Profile Files

- **`application.yml`** — Common configuration shared across all environments (always loaded)
- **`application-dev.yml`** — Development profile; activate with `SPRING_PROFILES_ACTIVE=dev`
- **`application-prod.yml`** — Production profile; active by default

## Rules

- Shared settings (e.g. common Spring Boot defaults) go in `application.yml`
- Environment-specific overrides go in the appropriate profile file
- Never put secrets or environment-specific URLs in `application.yml`
- Production secrets are injected via environment variables (see `docs/project/architecture.md` for the full list)
- Activating `dev` arms an authentication bypass — `DevAuthConfig` replaces JWT validation with a decoder that accepts
  any bearer token as the caller `<token>@local.test`. `prod` is the default active profile, and a startup `WARN`
  announces the bypass whenever it is on

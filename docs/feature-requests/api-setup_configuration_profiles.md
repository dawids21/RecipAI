## FEATURE:

Split current configuration into PROD and DEV in the backend app.
Leave common configuration in `application.yml` file.
Add line that activates `prod` profile by default.
Move current configuration into `application-dev.yml`
Create new configuration `application-prod.yml` (make database configurable using env variables, remove logging
configuration)

## EXAMPLES:

- None

## DOCUMENTATION:

- `backend/CLAUDE.md` - add info about new profiles, add guidelines to split new configuration between the files if they
  are related to PROD/DEV servers, otherwise they may be shared in the main configuration file
- `docs/backend/backend.md` - add info that app uses profiles to split configuration between environments

## OTHER CONSIDERATIONS:

- None

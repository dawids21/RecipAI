# Settings Module

Owns the caller's account-level operations. Today that is self-service account deletion: removing
everything an email owns or holds across every module. The module knows only the order in which
modules are purged — it holds no repositories, no entities, and no knowledge of what any other module
stores; each module purges its own data through its facade (see
`docs/ADRs/0010-account-deletion-orchestrated-over-per-module-purges.md`).

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── settings/
    ├── SettingsController.java      # Package-private @RestController — DELETE /settings/account, subject taken from the caller's email claim
    └── AccountDeletionService.java  # Package-private — owns the purge order; takes an email, not a Jwt, and calls facades only
```

The module has no `dto/`, `exception/`, exception handler or facade: it raises no exception of its own
and nothing outside it calls in.

## Behaviour

- **Purge order** — `planning`, `shoppinglists`, `recipes`, `recipes.collections`, then `limits`, then
  `permissions`. Each resource module asks `PermissionsFacade.ownedResources` what the email owns and
  deletes each resource through its ordinary per-resource delete, so `RecipeDeleted`, the S3 prefix
  delete, the limits release and `PermissionsFacade.resourceDeleted` all fire as for a single delete.
  `permissions` goes last because it is what answers "what does this email own"; any module holding
  owned resources must be purged before it, or its resources are left with no owner.
  - Meal plans go before recipes, so `RecipeDeleted` has none of the subject's own entries left to
    rewrite into placeholders — only other people's plans are touched.
  - Recipes go before collections, so a collection delete detaches only other people's recipes.
- **What survives** — a resource the subject held only as `EDITOR` is not deleted; only their
  `EDITOR` row goes. Other people's meal-plan entries for a purged recipe become placeholders.
- **No spanning transaction** — `AccountDeletionService` is deliberately not `@Transactional`; each
  module's purge is its own transaction. A failure part-way leaves earlier modules purged and later
  ones untouched, and the caller gets the failing module's status. Nothing records the partial state:
  the repair is to call the endpoint again, which is safe because every purge succeeds for an email
  with nothing left to remove (an account that owns nothing returns 204).
- **Missing email** — a null or blank email is refused with `IllegalArgumentException` before any
  module is touched, because derived deletes bind a null as `IS NULL`.
- **Logging** — the email is logged at `INFO` on entry and exit.

# Provisioning Module

Transformation module that converts ingredients (with quantity multipliers) into shopping list items;
exposes a `ProvisioningFacade` (no HTTP controller) for use by other modules. Appends ingredient
comments in parentheses to item names (e.g. `"salt (to taste)"`).

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── provisioning/
    ├── ProvisioningIngredient.java   # Input data model (public record)
    ├── ProvisioningItem.java         # Output data model (public record)
    ├── ProvisioningService.java      # Transformation logic (package-private)
    └── ProvisioningFacade.java       # Public facade for use by other modules
```

```
backend/src/test/java/xyz/stasiak/recipai/
└── provisioning/
    └── ProvisioningServiceTest.java   # Unit test of quantity scaling and ingredient naming
```

Shared test infrastructure is listed in `docs/backend/standards/integration-tests.md` → Shared Test
Infrastructure.

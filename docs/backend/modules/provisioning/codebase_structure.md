# Provisioning Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── provisioning/
    ├── ProvisioningIngredient.java   # Input data model (public record)
    ├── ProvisioningItem.java         # Output data model (public record)
    ├── ProvisioningService.java      # Transformation logic (package-private)
    └── ProvisioningFacade.java       # Public facade for use by other modules
```

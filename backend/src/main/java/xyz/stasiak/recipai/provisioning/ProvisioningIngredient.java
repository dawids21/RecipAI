package xyz.stasiak.recipai.provisioning;

import java.math.BigDecimal;

public record ProvisioningIngredient(
        String name,
        BigDecimal quantity,
        String unit,
        BigDecimal multiplier,
        String sourceName,
        String comment
) {
}

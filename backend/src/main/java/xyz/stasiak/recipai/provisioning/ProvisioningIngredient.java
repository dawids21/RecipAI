package xyz.stasiak.recipai.provisioning;

import java.math.BigDecimal;

public record ProvisioningIngredient(
        String name,
        String quantity,
        String unit,
        BigDecimal multiplier
) {
}

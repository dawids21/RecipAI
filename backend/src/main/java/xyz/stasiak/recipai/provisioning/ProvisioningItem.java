package xyz.stasiak.recipai.provisioning;

import java.math.BigDecimal;

public record ProvisioningItem(
        String name,
        BigDecimal quantity,
        String unit
) {
}

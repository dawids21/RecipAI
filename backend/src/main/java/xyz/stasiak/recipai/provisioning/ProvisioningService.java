package xyz.stasiak.recipai.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
class ProvisioningService {

    List<ProvisioningItem> provision(List<ProvisioningIngredient> ingredients) {
        return ingredients.stream()
                .map(ingredient -> new ProvisioningItem(
                        ingredient.name(),
                        applyMultiplier(parseQuantity(ingredient.quantity()), ingredient.multiplier()),
                        ingredient.unit()))
                .toList();
    }

    private BigDecimal parseQuantity(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(quantity);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal applyMultiplier(BigDecimal quantity, BigDecimal multiplier) {
        if (quantity == null) {
            return multiplier;
        }
        return quantity.multiply(multiplier);
    }
}

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
                .map(ingredient -> {
                    String name = (ingredient.comment() != null && !ingredient.comment().isBlank())
                            ? ingredient.name() + " (" + ingredient.comment() + ")"
                            : ingredient.name();
                    return new ProvisioningItem(
                            name,
                            applyMultiplier(ingredient.quantity(), ingredient.multiplier()),
                            ingredient.unit(),
                            ingredient.sourceName());
                })
                .toList();
    }

    private BigDecimal applyMultiplier(BigDecimal quantity, BigDecimal multiplier) {
        if (quantity == null) {
            return multiplier;
        }
        return quantity.multiply(multiplier);
    }
}

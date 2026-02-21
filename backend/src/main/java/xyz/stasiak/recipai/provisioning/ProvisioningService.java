package xyz.stasiak.recipai.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ProvisioningService {

    List<ProvisioningItem> provision(List<ProvisioningIngredient> ingredients) {
        return ingredients.stream()
                .map(ingredient -> new ProvisioningItem(ingredient.name(), ingredient.quantity(), ingredient.unit()))
                .toList();
    }
}

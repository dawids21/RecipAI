package xyz.stasiak.recipai.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProvisioningFacade {

    private final ProvisioningService provisioningService;

    public List<ProvisioningItem> provision(List<ProvisioningIngredient> ingredients) {
        return provisioningService.provision(ingredients);
    }
}

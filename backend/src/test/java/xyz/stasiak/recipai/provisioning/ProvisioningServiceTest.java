package xyz.stasiak.recipai.provisioning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningServiceTest {

    private final ProvisioningService provisioningService = new ProvisioningService();

    @Test
    void shouldMapIngredientToItemOneToOne() {
        var ingredients = List.of(
                new ProvisioningIngredient("Flour", "2", "cups"),
                new ProvisioningIngredient("Sugar", "1", "tbsp"),
                new ProvisioningIngredient("Salt", null, null)
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items).hasSize(3);
        assertThat(items.get(0)).isEqualTo(new ProvisioningItem("Flour", new BigDecimal("2"), "cups"));
        assertThat(items.get(1)).isEqualTo(new ProvisioningItem("Sugar", new BigDecimal("1"), "tbsp"));
        assertThat(items.get(2)).isEqualTo(new ProvisioningItem("Salt", null, null));
    }

    @Test
    void shouldReturnEmptyListForEmptyIngredients() {
        var items = provisioningService.provision(List.of());

        assertThat(items).isEmpty();
    }

    @Test
    void shouldPreserveIngredientOrder() {
        var ingredients = List.of(
                new ProvisioningIngredient("Eggs", "3", null),
                new ProvisioningIngredient("Butter", "100", "g"),
                new ProvisioningIngredient("Milk", "200", "ml")
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items).extracting(ProvisioningItem::name)
                .containsExactly("Eggs", "Butter", "Milk");
    }

    @Test
    void shouldReturnNullQuantityForUnparseableValue() {
        var ingredients = List.of(new ProvisioningIngredient("Flour", "abc", "cups"));

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().quantity()).isNull();
    }

    @Test
    void shouldReturnNullQuantityForBlankValue() {
        var ingredients = List.of(new ProvisioningIngredient("Sugar", "  ", "tbsp"));

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().quantity()).isNull();
    }
}

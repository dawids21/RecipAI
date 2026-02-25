package xyz.stasiak.recipai.provisioning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningServiceTest {

    private final ProvisioningService provisioningService = new ProvisioningService();

    @Test
    void shouldMultiplyNumericQuantityByMultiplier() {
        var ingredients = List.of(
                new ProvisioningIngredient("Flour", "2", "cups", new BigDecimal("3"), "Cake"),
                new ProvisioningIngredient("Sugar", "1", "tbsp", new BigDecimal("2"), "Cake"),
                new ProvisioningIngredient("Salt", null, null, BigDecimal.ONE, "Cake")
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).quantity()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(items.get(1).quantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(items.get(2).quantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(items.get(0).source()).isEqualTo("Cake");
        assertThat(items.get(1).source()).isEqualTo("Cake");
        assertThat(items.get(2).source()).isEqualTo("Cake");
    }

    @Test
    void shouldReturnMultiplierAsQuantityForNullQuantity() {
        var ingredients = List.of(
                new ProvisioningIngredient("Salt", null, null, new BigDecimal("3"), "Soup")
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(items.getFirst().source()).isEqualTo("Soup");
    }

    @Test
    void shouldReturnMultiplierAsQuantityForUnparseableValue() {
        var ingredients = List.of(new ProvisioningIngredient("Flour", "abc", "cups", new BigDecimal("2"), "Bread"));

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(items.getFirst().source()).isEqualTo("Bread");
    }

    @Test
    void shouldReturnMultiplierAsQuantityForBlankValue() {
        var ingredients = List.of(new ProvisioningIngredient("Sugar", "  ", "tbsp", new BigDecimal("4"), "Cookie"));

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(items.getFirst().source()).isEqualTo("Cookie");
    }

    @Test
    void shouldReturnEmptyListForEmptyIngredients() {
        var items = provisioningService.provision(List.of());

        assertThat(items).isEmpty();
    }

    @Test
    void shouldPreserveIngredientOrder() {
        var ingredients = List.of(
                new ProvisioningIngredient("Eggs", "3", null, BigDecimal.ONE, "Omelette"),
                new ProvisioningIngredient("Butter", "100", "g", BigDecimal.ONE, "Omelette"),
                new ProvisioningIngredient("Milk", "200", "ml", BigDecimal.ONE, "Omelette")
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items).extracting(ProvisioningItem::name)
                .containsExactly("Eggs", "Butter", "Milk");
    }
}

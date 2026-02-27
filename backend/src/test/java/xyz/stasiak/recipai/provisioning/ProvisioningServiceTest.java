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
                new ProvisioningIngredient("Flour", new BigDecimal(2), "cups", new BigDecimal(3), "Cake", null),
                new ProvisioningIngredient("Sugar", new BigDecimal(1), "tbsp", new BigDecimal(2), "Cake", null),
                new ProvisioningIngredient("Salt", null, null, BigDecimal.ONE, "Cake", null)
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
                new ProvisioningIngredient("Salt", null, null, new BigDecimal("3"), "Soup", null)
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(items.getFirst().source()).isEqualTo("Soup");
    }

    @Test
    void shouldReturnEmptyListForEmptyIngredients() {
        var items = provisioningService.provision(List.of());

        assertThat(items).isEmpty();
    }

    @Test
    void shouldPreserveIngredientOrder() {
        var ingredients = List.of(
                new ProvisioningIngredient("Eggs", new BigDecimal(3), null, BigDecimal.ONE, "Omelette", null),
                new ProvisioningIngredient("Butter", new BigDecimal(100), "g", BigDecimal.ONE, "Omelette", null),
                new ProvisioningIngredient("Milk", new BigDecimal(200), "ml", BigDecimal.ONE, "Omelette", null)
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items).extracting(ProvisioningItem::name)
                .containsExactly("Eggs", "Butter", "Milk");
    }

    @Test
    void shouldPrependCommentToIngredientName() {
        var ingredients = List.of(
                new ProvisioningIngredient("salt", null, null, new BigDecimal(2), "Soup", "to taste")
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().name()).isEqualTo("salt (to taste)");
        assertThat(items.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void shouldNotPrependNullComment() {
        var ingredients = List.of(
                new ProvisioningIngredient("Flour", new BigDecimal(300), "g", BigDecimal.ONE, "Bread", null)
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().name()).isEqualTo("Flour");
    }

    @Test
    void shouldNotPrependBlankComment() {
        var ingredients = List.of(
                new ProvisioningIngredient("Flour", new BigDecimal(300), "g", BigDecimal.ONE, "Bread", "  ")
        );

        var items = provisioningService.provision(ingredients);

        assertThat(items.getFirst().name()).isEqualTo("Flour");
    }
}

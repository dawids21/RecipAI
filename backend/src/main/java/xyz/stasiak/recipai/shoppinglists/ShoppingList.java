package xyz.stasiak.recipai.shoppinglists;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "shopping_lists")
@Getter
@Setter
@ToString
@NoArgsConstructor
class ShoppingList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Instant lastModified = Instant.now();

    @OneToMany(mappedBy = "shoppingList", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("position ASC")
    @ToString.Exclude
    private List<ShoppingListItem> items = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ShoppingList that = (ShoppingList) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    ShoppingListItem addItem(String name, BigDecimal quantity, String unit) {
        // Calculate next position
        int nextPosition;
        if (items.isEmpty()) {
            nextPosition = 1;
        } else {
            nextPosition = items.getLast().getPosition() + 1;
        }

        // Create new item
        ShoppingListItem item = new ShoppingListItem();
        item.setName(name);
        item.setQuantity(quantity);
        item.setUnit(unit);
        item.setPosition(nextPosition);

        // Set bidirectional relationship
        item.setShoppingList(this);

        // Add to collection
        items.add(item);

        lastModified = Instant.now();

        return item;
    }

    void removeItem(UUID itemId) {
        // Find item by ID (idempotent - no exception if not found)
        Optional<ShoppingListItem> itemToRemove = items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();

        // If not found, return immediately (idempotent behavior)
        if (itemToRemove.isEmpty()) {
            return;
        }

        // Remove from collection (orphanRemoval handles deletion)
        items.remove(itemToRemove.get());

        // CRITICAL: Recalculate positions for ALL remaining items
        // This maintains strict integer consistency: 1, 2, 3, ...
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i + 1);
        }

        lastModified = Instant.now();
    }
}

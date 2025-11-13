package xyz.stasiak.recipai.shoppinglists;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_items")
@Getter
@Setter
@ToString
@NoArgsConstructor
class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(length = 64)
    private String unit;

    @Column(nullable = false)
    private Integer position;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ShoppingListItem that = (ShoppingListItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
package xyz.stasiak.recipai.shoppinglists;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Column(name = "shopping_list_id", nullable = false)
    private UUID shoppingListId;

    @Column(nullable = false)
    private String name;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(length = 64)
    private String unit;

    @Column(nullable = false)
    private Boolean checked = false;

    @Column(precision = 21, scale = 12, nullable = false)
    private BigDecimal position;

    @Version
    @Column(nullable = false)
    private Long version;

    ShoppingListItem(UUID shoppingListId, String name, BigDecimal quantity, String unit, boolean checked, BigDecimal position) {
        this.shoppingListId = shoppingListId;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.position = normalizePositionScale(position);
        this.checked = checked;
    }

    void check() {
        this.checked = true;
    }

    void uncheck() {
        this.checked = false;
    }

    void setPosition(BigDecimal position) {
        this.position = normalizePositionScale(position);
    }

    private static BigDecimal normalizePositionScale(BigDecimal position) {
        return position != null ? position.setScale(12, RoundingMode.HALF_UP) : null;
    }

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
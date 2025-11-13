package xyz.stasiak.recipai.shoppinglists;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
}

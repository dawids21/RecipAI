package xyz.stasiak.recipai.shoppinglists;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "shopping_list_item_checkbox")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ShoppingListItemCheckbox {

    @Id
    @Column(name = "shopping_list_item_id")
    private UUID shoppingListItemId;

    @Column(nullable = false)
    private Boolean checked;

    @Version
    @Column(nullable = false)
    private Long version;

    ShoppingListItemCheckbox(UUID shoppingListItemId, Boolean checked) {
        this.shoppingListItemId = shoppingListItemId;
        this.checked = checked;
    }
}

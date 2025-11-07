package xyz.stasiak.recipai.shoppinglists;

import java.math.BigDecimal;
import java.util.UUID;

interface ShoppingListView {
    // List properties (same for all rows)
    UUID getListId();

    String getListName();

    Long getListVersion();

    // Item properties (different for each row, null if no items)
    UUID getItemId();

    String getItemName();

    BigDecimal getItemQuantity();

    String getItemUnit();

    Integer getItemPosition();

    Boolean getItemChecked();
}
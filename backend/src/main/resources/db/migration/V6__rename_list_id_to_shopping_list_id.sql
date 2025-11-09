-- Rename list_id to shopping_list_id in shopping_list_items table
ALTER TABLE shopping_list_items
    RENAME COLUMN list_id TO shopping_list_id;

-- Rename the foreign key constraint for consistency
ALTER TABLE shopping_list_items
DROP
CONSTRAINT fk_shopping_list_items_list_id;

ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_shopping_list_items_shopping_list_id
        FOREIGN KEY (shopping_list_id)
            REFERENCES shopping_lists (id)
            ON DELETE CASCADE;